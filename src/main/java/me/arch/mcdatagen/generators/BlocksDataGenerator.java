package me.arch.mcdatagen.generators;

import com.google.common.base.CaseFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.arch.mcdatagen.util.DataGeneratorUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BlocksDataGenerator implements IDataGenerator {

    //TODO really need to do ImmutableMap<Material, String> since materials have no actual identifiers or registries
    private static String guessMaterialName(Material material) {
        return "UNKNOWN";
    }

    private static List<Item> getItemsEffectiveForBlock(BlockState blockState) {
        return Registry.ITEM.stream()
                .filter(item -> item.getDefaultStack().isSuitableFor(blockState))
                .collect(Collectors.toList());
    }

    private static void populateDropsIfPossible(BlockState blockState, Item firstToolItem, List<ItemStack> outDrops) {
        MinecraftServer minecraftServer = DataGeneratorUtils.getCurrentlyRunningServer();
        if (minecraftServer != null) {
            //If we have local world context, we can actually evaluate loot tables and determine actual data
            ServerWorld serverWorld = minecraftServer.getOverworld();
            LootContext.Builder lootContext = new LootContext.Builder(serverWorld)
                    .parameter(LootContextParameters.BLOCK_STATE, blockState)
                    .parameter(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .parameter(LootContextParameters.TOOL, firstToolItem.getDefaultStack())
                    .random(0L);
            outDrops.addAll(blockState.getDroppedStacks(lootContext));
        } else {
            //If we're lacking world context to correctly determine drops, assume that default drop is ItemBlock stack in quantity of 1
            Item itemBlock = blockState.getBlock().asItem();
            if (itemBlock != Items.AIR) {
                outDrops.add(itemBlock.getDefaultStack());
            }
        }
    }

    private static String getPropertyTypeName(Property<?> property) {
        //Explicitly handle default minecraft properties
        if (property instanceof BooleanProperty) {
            return "bool";
        }
        if (property instanceof IntProperty) {
            return "int";
        }
        if (property instanceof EnumProperty) {
            return "enum";
        }

        //Use simple class name as fallback, this code will give something like
        //example_type for ExampleTypeProperty class name
        String rawPropertyName = property.getClass().getSimpleName().replace("Property", "");
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, rawPropertyName);
    }

    private static <T extends Comparable<T>> JsonObject generateStateProperty(Property<T> property) {
        JsonObject propertyObject = new JsonObject();
        Collection<T> propertyValues = property.getValues();

        propertyObject.addProperty("name", property.getName());
        propertyObject.addProperty("type", getPropertyTypeName(property));
        propertyObject.addProperty("num_values", propertyValues.size());

        //Do not add values for vanilla boolean properties, they are known by default
        if (!(property instanceof BooleanProperty)) {
            JsonArray propertyValuesArray = new JsonArray();
            for (T propertyValue : propertyValues) {
                propertyValuesArray.add(property.name(propertyValue));
            }
            propertyObject.add("values", propertyValuesArray);
        }
        return propertyObject;
    }

    @Override
    public String getDataName() {
        return "blocks";
    }

    @Override
    public JsonArray generateDataJson() {
        JsonArray resultBlocksArray = new JsonArray();
        Registry<Block> blockRegistry = Registry.BLOCK;
        blockRegistry.forEach(block -> resultBlocksArray.add(generateBlock(blockRegistry, block)));
        return resultBlocksArray;
    }

    public static JsonObject generateBlock(Registry<Block> blockRegistry, Block block) {
        JsonObject blockDesc = new JsonObject();

        List<BlockState> blockStates = block.getStateManager().getStates();
        BlockState defaultState = block.getDefaultState();
        Identifier registryKey = blockRegistry.getKey(block).orElseThrow().getValue();
        Item blockItem = block.asItem();
        String localizationKey = blockItem.getTranslationKey();

        blockDesc.addProperty("id", blockRegistry.getRawId(block));
        blockDesc.addProperty("name", registryKey.getPath());
        blockDesc.addProperty("displayName", DataGeneratorUtils.translateText(localizationKey));

        blockDesc.addProperty("hardness", block.getHardness());
        blockDesc.addProperty("resistance", block.getBlastResistance());
        blockDesc.addProperty("stackSize", blockItem.getMaxCount());
        blockDesc.addProperty("diggable", block.getHardness() >= 0.0f);
        blockDesc.addProperty("material", guessMaterialName(defaultState.getMaterial()));

        blockDesc.addProperty("transparent", !defaultState.isOpaque());
        blockDesc.addProperty("emitLight", defaultState.getLuminance());
        blockDesc.addProperty("filterLight", defaultState.getOpacity(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));

        blockDesc.addProperty("defaultState", Block.getRawIdFromState(defaultState));
        blockDesc.addProperty("minStateId", Block.getRawIdFromState(blockStates.get(0)));
        blockDesc.addProperty("maxStateId", Block.getRawIdFromState(blockStates.get(blockStates.size() - 1)));

        JsonArray stateProperties = new JsonArray();
        for (Property<?> property : block.getStateManager().getProperties()) {
            stateProperties.add(generateStateProperty(property));
        }
        blockDesc.add("states", stateProperties);

        List<Item> effectiveTools = getItemsEffectiveForBlock(defaultState);
        JsonObject effectiveToolsObject = new JsonObject();
        for (Item effectiveItem : effectiveTools) {
            effectiveToolsObject.addProperty(Integer.toString(Item.getRawId(effectiveItem)), true);
        }
        blockDesc.add("harvestTools", effectiveToolsObject);

        List<ItemStack> actualBlockDrops = new ArrayList<>();
        populateDropsIfPossible(defaultState, effectiveTools.isEmpty() ? Items.AIR : effectiveTools.get(0), actualBlockDrops);

        JsonArray dropsArray = new JsonArray();
        for (ItemStack dropStack : actualBlockDrops) {
            dropsArray.add(Item.getRawId(dropStack.getItem()));
        }
        blockDesc.add("drops", dropsArray);

        VoxelShape blockCollisionShape = defaultState.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        blockDesc.addProperty("boundingBox", blockCollisionShape.isEmpty() ? "empty" : "block");

        return blockDesc;
    }
}
