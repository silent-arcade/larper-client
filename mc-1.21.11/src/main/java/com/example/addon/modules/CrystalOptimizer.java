package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;

import java.util.ArrayList;
import java.util.List;

public class CrystalOptimizer extends Module {
    private final List<Integer> hit = new ArrayList<>();

    public CrystalOptimizer() {
        super(AddonTemplate.CATEGORY, "crystal-optimizer", "Removes a crystal on your screen the moment you hit it, so you can place the next one sooner.");
    }

    @Override
    public void onDeactivate() {
        hit.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof EndCrystalEntity) hit.add(event.entity.getId());
    }

    // after the attack packet went out, drop it client-side instead of waiting for the server
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) {
            hit.clear();
            return;
        }
        for (int id : hit) if (mc.world.getEntityById(id) != null) mc.world.removeEntity(id, Entity.RemovalReason.KILLED);
        hit.clear();
    }
}
