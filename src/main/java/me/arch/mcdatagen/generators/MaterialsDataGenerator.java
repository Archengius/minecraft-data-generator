package me.arch.mcdatagen.generators;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.arch.mcdatagen.mixin.MiningToolItemAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.registry.Registry;

import java.util.*;
import java.util.function.Predicate;

//TODO entire idea of linking materials to tool speeds is obsolete and just wrong now,
//TODO but we kinda have to support it to let old code work for computing digging times,
//TODO so for now we will handle materials as "virtual" ones based on which tools can break blocks
public class MaterialsDataGenerator implements IDataGenerator {

    @Override
    public String getDataName() {
        return "materials";
    }

    private static String makeMaterialNameForTag(Tag<Block> tag) {
        Tag.Identified<Block> identifiedTag = (Tag.Identified<Block>) tag;
        return identifiedTag.getId().getPath();
    }

    public record MaterialInfo(String materialName, Predicate<BlockState> predicate) {
    }

    public static List<MaterialInfo> getGlobalMaterialInfo() {
        ArrayList<MaterialInfo> resultList = new ArrayList<>();

        resultList.add(new MaterialInfo("vine", blockState -> blockState.isOf(Blocks.VINE)));
        resultList.add(new MaterialInfo("glow_lichen", blockState -> blockState.isOf(Blocks.GLOW_LICHEN)));
        resultList.add(new MaterialInfo("coweb", blockState -> blockState.isOf(Blocks.COBWEB)));

        resultList.add(new MaterialInfo("leaves", blockState -> blockState.isIn(BlockTags.LEAVES)));
        resultList.add(new MaterialInfo("wool", blockState -> blockState.isIn(BlockTags.WOOL)));
        resultList.add(new MaterialInfo("ground", blockState -> blockState.getMaterial() == Material.GOURD));

        resultList.add(new MaterialInfo("plant", blockState ->
                blockState.getMaterial() == Material.PLANT || blockState.getMaterial() == Material.REPLACEABLE_PLANT));

        HashSet<String> uniqueMaterialNames = new HashSet<>();

        Registry<Item> itemRegistry = Registry.ITEM;
        itemRegistry.forEach(item -> {
            if (item instanceof MiningToolItem toolItem) {
                Tag<Block> effectiveBlocks = ((MiningToolItemAccessor) toolItem).getEffectiveBlocks();
                String materialName = makeMaterialNameForTag(effectiveBlocks);

                if (!uniqueMaterialNames.contains(materialName)) {
                    uniqueMaterialNames.add(materialName);
                    resultList.add(new MaterialInfo(materialName, blockState -> blockState.isIn(effectiveBlocks)));
                }
            }
        });

        return resultList;
    }

    @Override
    public JsonElement generateDataJson() {
        Registry<Item> itemRegistry = Registry.ITEM;

        Map<String, Map<Item, Float>> materialMiningSpeeds = new HashMap<>();
        materialMiningSpeeds.put("default", ImmutableMap.of());

        //Special materials used for shears and swords special mining speed logic
        Map<Item, Float> leavesMaterialSpeeds = new HashMap<>();
        Map<Item, Float> cowebMaterialSpeeds = new HashMap<>();
        Map<Item, Float> plantMaterialSpeeds = new HashMap<>();
        Map<Item, Float> gourdMaterialSpeeds = new HashMap<>();

        materialMiningSpeeds.put(makeMaterialNameForTag(BlockTags.LEAVES), leavesMaterialSpeeds);
        materialMiningSpeeds.put("coweb", cowebMaterialSpeeds);
        materialMiningSpeeds.put("plant", plantMaterialSpeeds);
        materialMiningSpeeds.put("gourd", gourdMaterialSpeeds);

        //Shears need special handling because they do not follow normal rules like tools
        leavesMaterialSpeeds.put(Items.SHEARS, 15.0f);
        cowebMaterialSpeeds.put(Items.SHEARS, 15.0f);
        materialMiningSpeeds.put("vine", ImmutableMap.of(Items.SHEARS, 2.0f));
        materialMiningSpeeds.put("glow_lichen", ImmutableMap.of(Items.SHEARS, 2.0f));
        materialMiningSpeeds.put("wool", ImmutableMap.of(Items.SHEARS, 5.0f));

        itemRegistry.forEach(item -> {
            //Tools are handled rather easily and do not require anything else
            if (item instanceof MiningToolItem toolItem) {
                Tag<Block> effectiveBlocks = ((MiningToolItemAccessor) toolItem).getEffectiveBlocks();
                String materialName = makeMaterialNameForTag(effectiveBlocks);

                Map<Item, Float> materialSpeeds = materialMiningSpeeds.computeIfAbsent(materialName, k -> new HashMap<>());
                float miningSpeed = ((MiningToolItemAccessor) toolItem).getMiningSpeed();
                materialSpeeds.put(item, miningSpeed);
            }

            //Swords require special treatment
            if (item instanceof SwordItem) {
                cowebMaterialSpeeds.put(item, 15.0f);
                leavesMaterialSpeeds.put(item, 1.5f);
                plantMaterialSpeeds.put(item, 1.5f);
                gourdMaterialSpeeds.put(item, 1.5f);
            }
        });

        JsonObject resultObject = new JsonObject();

        for (var entry : materialMiningSpeeds.entrySet()) {
            JsonObject toolSpeedsObject = new JsonObject();

            for (var toolEntry : entry.getValue().entrySet()) {
                int rawItemId = itemRegistry.getRawId(toolEntry.getKey());
                toolSpeedsObject.addProperty(Integer.toString(rawItemId), toolEntry.getValue());
            }
            resultObject.add(entry.getKey(), toolSpeedsObject);
        }

        return resultObject;
    }
}
