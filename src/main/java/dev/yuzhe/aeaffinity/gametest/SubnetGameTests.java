package dev.yuzhe.aeaffinity.gametest;

import appeng.api.config.Actionable;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.cells.BasicCellInventory;
import appeng.me.helpers.BaseActionSource;
import dev.yuzhe.aeaffinity.config.ActivationMode;
import dev.yuzhe.aeaffinity.config.AeAffinityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("aeaffinity")
@PrefixGameTestTemplate(false)
public final class SubnetGameTests {
    private static final BlockPos PARENT_ENERGY = new BlockPos(1, 2, 2);
    private static final BlockPos PARENT_DRIVE = new BlockPos(2, 2, 2);
    private static final BlockPos PARENT_BUS = new BlockPos(3, 2, 2);
    private static final BlockPos CHILD_INTERFACE = new BlockPos(4, 2, 2);
    private static final BlockPos CHILD_ENERGY = new BlockPos(4, 2, 3);
    private static final BlockPos CHILD_DRIVE = new BlockPos(5, 2, 3);
    private static final BlockPos CHILD_BUS = new BlockPos(4, 2, 4);
    private static final BlockPos CHILD_CHEST = new BlockPos(4, 2, 5);

    private SubnetGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "subnet")
    public static void childQuoteTracksSafeRoutes(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setup(helper, helmet, true);

        helper.runAfterDelay(100, () -> {
            var cell = fixture.parentDrive().getCellInventory(0);
            helper.assertTrue(cell != null && cell.getAvailableStacks().get(helmet) == 1,
                    "unsafe child network accepted a migration quote");
            helper.assertTrue(count(fixture.childChest(), Items.IRON_HELMET) == 0,
                    "unsafe child route received the helmet");
            helper.destroyBlock(CHILD_DRIVE);
        });

        helper.runAfterDelay(400, () -> {
            var cell = fixture.parentDrive().getCellInventory(0);
            helper.assertTrue(cell != null, "parent drive did not mount item cell");
            long inCell = cell.getAvailableStacks().get(helmet);
            long inChest = count(fixture.childChest(), Items.IRON_HELMET);
            helper.assertTrue(inChest == 1,
                    "child quote did not recover after unsafe route removal; parent=" + inCell + ", child=" + inChest);
            helper.assertTrue(inCell == 0 && inCell + inChest == 1,
                    "helmet count was not conserved across the subnet boundary");
            helper.destroyBlock(PARENT_BUS);
            helper.destroyBlock(CHILD_INTERFACE);
            helper.destroyBlock(CHILD_BUS);
            helper.succeed();
        });
    }

    private static Fixture setup(GameTestHelper helper, AEItemKey key, boolean addVoidCell) {
        AeAffinityConfig.ACTIVATION.set(ActivationMode.ALL);
        AeAffinityConfig.CHARGE_ENERGY.set(true);
        AeAffinityConfig.MIN_IDLE_TICKS.set(20);
        AeAffinityConfig.MAX_IDLE_TICKS.set(20);
        AeAffinityConfig.PLANNING_TICKS.set(1);

        helper.setBlock(PARENT_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(PARENT_DRIVE, AEBlocks.DRIVE.block());
        helper.setBlock(CHILD_INTERFACE, AEBlocks.INTERFACE.block());
        helper.setBlock(CHILD_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        if (addVoidCell) {
            helper.setBlock(CHILD_DRIVE, AEBlocks.DRIVE.block());
        }
        helper.setBlock(CHILD_CHEST, Blocks.CHEST);

        var player = helper.makeMockPlayer(GameType.CREATIVE);
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(PARENT_BUS), Direction.UP, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(PARENT_BUS), Direction.EAST, player,
                AEParts.STORAGE_BUS.asItem());
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(CHILD_BUS), Direction.UP, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(CHILD_BUS), Direction.SOUTH, player,
                AEParts.STORAGE_BUS.asItem());

        var parentCellStack = AEItems.ITEM_CELL_1K.stack();
        var parentCell = BasicCellInventory.createInventory(parentCellStack, null);
        helper.assertTrue(parentCell.insert(key, 1, Actionable.MODULATE, new BaseActionSource()) == 1,
                "failed to seed parent cell");
        DriveBlockEntity parentDrive = helper.getBlockEntity(PARENT_DRIVE);
        helper.assertTrue(parentDrive.getInternalInventory().addItems(parentCellStack).isEmpty(),
                "failed to install parent cell");

        if (addVoidCell) {
            var childCellStack = AEItems.ITEM_CELL_1K.stack();
            var childCell = BasicCellInventory.createInventory(childCellStack, null);
            childCell.getUpgradesInventory().addItems(AEItems.VOID_CARD.stack());
            DriveBlockEntity childDrive = helper.getBlockEntity(CHILD_DRIVE);
            helper.assertTrue(childDrive.getInternalInventory().addItems(childCellStack).isEmpty(),
                    "failed to install child Void Cell");
        }

        ChestBlockEntity childChest = helper.getBlockEntity(CHILD_CHEST);
        return new Fixture(parentDrive, childChest);
    }

    private static long count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        long count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private record Fixture(DriveBlockEntity parentDrive, ChestBlockEntity childChest) {
    }
}
