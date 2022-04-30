package io.github.apace100.origins.badge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.apace100.apoli.integration.PostPowerLoadCallback;
import io.github.apace100.apoli.integration.PostPowerReloadCallback;
import io.github.apace100.apoli.integration.PrePowerReloadCallback;
import io.github.apace100.apoli.power.*;
import io.github.apace100.calio.registry.DataObjectRegistry;
import io.github.apace100.origins.Origins;
import io.github.apace100.origins.integration.AutoBadgeCallback;
import io.github.apace100.origins.networking.ModPackets;
import io.github.apace100.origins.util.PowerKeyManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

import java.util.*;

public final class BadgeManager {
    public static final DataObjectRegistry<Badge> REGISTRY = new DataObjectRegistry.Builder<>(Origins.identifier("badge"), Badge.class)
        .readFromData("badges", true)
        .dataErrorHandler((id, exception) -> Origins.LOGGER.error("Failed to read badge " + ", caused by", exception))
        .defaultFactory(BadgeFactories.TOOLTIP)
        .buildAndRegister();
    private static final Map<Identifier, List<Badge>> BADGES = new HashMap<>();
    private static final Map<Identifier, List<Identifier>> MULTIPLE_POWERS = new HashMap<>();

    private static final Identifier TOGGLE_BADGE_SPRITE = Origins.identifier("textures/gui/badge/toggle.png");
    private static final Identifier ACTIVE_BADGE_SPRITE = Origins.identifier("textures/gui/badge/active.png");
    private static final Identifier RECIPE_BADGE_SPRITE = Origins.identifier("textures/gui/badge/recipe.png");

    public static void init() {
        //register builtin badge types
        register(BadgeFactories.SPRITE);
        register(BadgeFactories.TOOLTIP);
        register(BadgeFactories.CRAFTING_RECIPE);
        //register callbacks
        PrePowerReloadCallback.EVENT.register(BadgeManager::clear);
        PowerTypes.registerAdditionalData("badges", BadgeManager::readCustomBadges);
        PostPowerLoadCallback.EVENT.register(BadgeManager::readAutoBadges);
        AutoBadgeCallback.EVENT.register(BadgeManager::createAutoBadges);
        PostPowerReloadCallback.EVENT.register(BadgeManager::mergeMultiplePowerBadges);
    }

    public static void register(BadgeFactory factory) {
        REGISTRY.registerFactory(factory.id(), factory);
    }

    public static void putPowerBadge(Identifier powerId, Badge badge) {
        List<Badge> badgeList = BADGES.computeIfAbsent(powerId, id -> new LinkedList<>());
        badgeList.add(badge);
    }

    public static List<Badge> getPowerBadges(Identifier powerId) {
        return BADGES.computeIfAbsent(powerId, id -> new LinkedList<>());
    }

    public static void clear() {
        BADGES.clear();
    }

    public static void sync(ServerPlayerEntity player) {
        REGISTRY.sync(player);
        PacketByteBuf badgeData = new PacketByteBuf(Unpooled.buffer());
        badgeData.writeInt(BADGES.size());
        BADGES.forEach((id, list) -> {
            badgeData.writeIdentifier(id);
            badgeData.writeInt(list.size());
            list.forEach(badge -> badge.writeBuf(badgeData));
        });
        ServerPlayNetworking.send(player, ModPackets.BADGE_LIST, badgeData);
    }

    public static void readCustomBadges(Identifier powerId, Identifier factoryId, boolean isSubPower, JsonElement data, PowerType<?> powerType) {
        if(!powerType.isHidden() || isSubPower) {
            if(data.isJsonArray()) {
                for(JsonElement badgeJson : data.getAsJsonArray()) {
                    if(badgeJson.isJsonPrimitive()) {
                        Identifier badgeId = Identifier.tryParse(badgeJson.getAsString());
                        if(badgeId != null) {
                            Badge badge = REGISTRY.get(badgeId);
                            if(badge != null) {
                                putPowerBadge(powerId, badge);
                            } else {
                                Origins.LOGGER.error("\"badges\" field in power \"{}\" is referring a undefined badge \"{}\" !", powerId, badgeId);
                            }
                        } else {
                            Origins.LOGGER.error("\"badges\" field in power \"{}\" is not a valid identifier!", powerId);
                        }
                    } else if(badgeJson.isJsonObject()) {
                        try {
                            putPowerBadge(powerId, REGISTRY.readDataObject(badgeJson));
                        } catch(Exception exception) {
                            Origins.LOGGER.error("\"badges\" field in power \"" + powerId
                                + "\" contained an JSON object entry that cannot be resolved!", exception);
                        }
                    } else {
                        Origins.LOGGER.error("\"badges\" field in power \"" + powerId
                            + "\" contained an entry that was a JSON array, which is not allowed!");
                    }
                }
            } else {
                Origins.LOGGER.error("\"badges\" field in power \"" + powerId + "\" should be an array.");
            }
        }
    }

    public static void readAutoBadges(Identifier powerId, Identifier factoryId, boolean isSubPower, JsonObject json, PowerType<?> powerType) {
        if(powerType instanceof MultiplePowerType<?> mp) {
            MULTIPLE_POWERS.put(powerId, mp.getSubPowers());
        } else if((!BADGES.containsKey(powerId) || BADGES.get(powerId).size() == 0) && (!powerType.isHidden() || isSubPower)) {
            AutoBadgeCallback.EVENT.invoker().createAutoBadge(powerId, powerType);
        }
    }

    public static void createAutoBadges(Identifier powerId, PowerType<?> powerType) {
        Power power = powerType.create(null);
        if(power instanceof Active active) {
            boolean toggle = active instanceof TogglePower || active instanceof ToggleNightVisionPower;
            Text keyText = new LiteralText("[")
                .append(KeyBinding.getLocalizedName(PowerKeyManager.getKeyIdentifier(powerId)).get())
                .append(new LiteralText("]"));
            putPowerBadge(powerId, new TooltipBadge(toggle ? TOGGLE_BADGE_SPRITE : ACTIVE_BADGE_SPRITE,
                toggle ? new TranslatableText("origins.gui.badge.toggle", keyText)
                    : new TranslatableText("origins.gui.badge.active", keyText)
            ));
        } else if(power instanceof RecipePower recipePower) {
            Recipe<CraftingInventory> recipe = recipePower.getRecipe();
            String type = recipe instanceof ShapedRecipe ? "shaped" : "shapeless";
            putPowerBadge(powerId, new CraftingRecipeBadge(RECIPE_BADGE_SPRITE, recipe,
                new TranslatableText("origins.gui.badge.recipe.crafting." + type), null
            ));
        }
    }

    public static void mergeMultiplePowerBadges() {
        MULTIPLE_POWERS.forEach((powerId, subPowerIds) ->
            subPowerIds.forEach(subPowerId ->
                BADGES.computeIfAbsent(powerId, id -> new LinkedList<>())
                    .addAll(BADGES.computeIfAbsent(subPowerId, id -> new LinkedList<>()))
            )
        );
        MULTIPLE_POWERS.clear();
    }

}
