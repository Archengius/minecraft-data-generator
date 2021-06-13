package me.arch.mcdatagen;

import me.arch.mcdatagen.command.GenerateDataCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;

public class MCDataGenerator implements ModInitializer {

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
				GenerateDataCommand.register(dispatcher));
	}
}
