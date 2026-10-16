package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.ArrayList;

/** Turn it on and every other active module (Meteor's and every addon's) is switched off, then it turns itself off. */
public class TurnOffAll extends Module {
    public TurnOffAll() {
        super(AddonTemplate.CATEGORY, "turn-off-all", "Turns off every module that's on.");
    }

    @Override
    public void onActivate() {
        // Copy first: toggling changes the active list while we walk it
        int count = 0;
        for (Module m : new ArrayList<>(Modules.get().getActive())) {
            if (m == this) continue;
            m.toggle();
            count++;
        }
        info("Turned off %d module%s.", count, count == 1 ? "" : "s");
        toggle();
    }
}
