package io.github.apace100.origins.badge;

import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import io.github.apace100.calio.registry.DataObjectFactory;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SpriteBadge(Identifier spriteId) implements Badge {

    public SpriteBadge(SerializableData.Instance instance) {
        this(instance.getId("sprite"));
    }

    @Override
    public boolean hasTooltip() {
        return false;
    }

    @Override
    public List<TooltipComponent> getTooltipComponents(TextRenderer textRenderer, int widthLimit, float time) {
        return new ArrayList<>();
    }

    @Override
    public SerializableData.Instance toData(SerializableData.Instance instance) {
        instance.set("sprite", spriteId);
        return instance;
    }

    @Override
    public BadgeFactory getBadgeFactory() {
        return BadgeFactories.SPRITE;
    }

}