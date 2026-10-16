package com.example.addon.schematic;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Schematic {
    public record Block3(BlockPos pos, BlockState state) {}

    public final String name;
    public final int sizeX, sizeY, sizeZ;
    public final List<Block3> blocks;

    private Schematic(String name, int sizeX, int sizeY, int sizeZ, List<Block3> blocks) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = Collections.unmodifiableList(blocks);
    }

    private static final long MAX_BYTES = 64L << 20;
    private static final int MAX_BLOCKS = 2_000_000;

    public static Schematic load(Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_BYTES));
        String n = file.getFileName().toString();
        String lower = n.toLowerCase(Locale.ROOT);
        Schematic s;
        if (lower.endsWith(".litematic")) s = litematic(n, root);
        else if (lower.endsWith(".schem")) s = sponge(n, root);
        else if (lower.endsWith(".nbt")) s = structure(n, root);
        else throw new IOException("unknown schematic type (use .litematic, .schem or .nbt)");
        if (s.blocks.isEmpty()) throw new IOException("the schematic has no blocks");
        return s;
    }

    private static HolderGetter<Block> blockLookup() {
        return BuiltInRegistries.BLOCK;
    }

    private static Schematic structure(String name, CompoundTag root) throws IOException {
        ListTag size = root.getListOrEmpty("size");
        if (size.size() != 3) throw new IOException("not a structure file (no size)");
        ListTag palette = root.contains("palette") ? root.getListOrEmpty("palette") : root.getListOrEmpty("palettes").getListOrEmpty(0);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) states.add(NbtUtils.readBlockState(blockLookup(), palette.getCompoundOrEmpty(i)));
        ListTag list = root.getListOrEmpty("blocks");
        List<Block3> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompoundOrEmpty(i);
            ListTag pos = b.getListOrEmpty("pos");
            int idx = b.getIntOr("state", -1);
            if (pos.size() != 3 || idx < 0 || idx >= states.size()) continue;
            add(out, new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)), states.get(idx));
        }
        return new Schematic(name, size.getIntOr(0, 0), size.getIntOr(1, 0), size.getIntOr(2, 0), out);
    }

    private static Schematic litematic(String name, CompoundTag root) throws IOException {
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        if (regions.keySet().isEmpty()) throw new IOException("no regions in the litematic");
        List<Block3> raw = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : regions.keySet()) {
            CompoundTag r = regions.getCompoundOrEmpty(key);
            CompoundTag pos = r.getCompoundOrEmpty("Position"), size = r.getCompoundOrEmpty("Size");
            int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
            int ox = pos.getIntOr("x", 0) + (sx < 0 ? sx + 1 : 0), oy = pos.getIntOr("y", 0) + (sy < 0 ? sy + 1 : 0), oz = pos.getIntOr("z", 0) + (sz < 0 ? sz + 1 : 0);
            sx = Math.abs(sx);
            sy = Math.abs(sy);
            sz = Math.abs(sz);
            if ((long) sx * sy * sz > MAX_BLOCKS) throw new IOException("region " + key + " is too big");
            ListTag pal = r.getListOrEmpty("BlockStatePalette");
            List<BlockState> states = new ArrayList<>();
            for (int i = 0; i < pal.size(); i++) states.add(NbtUtils.readBlockState(blockLookup(), pal.getCompoundOrEmpty(i)));
            long[] data = r.getLongArray("BlockStates").orElse(new long[0]);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, states.size() - 1)));
            long mask = (1L << bits) - 1;
            long volume = (long) sx * sy * sz;
            for (long i = 0; i < volume; i++) {
                long start = i * bits;
                int startLong = (int) (start >> 6), endLong = (int) ((start + bits - 1) >> 6), offset = (int) (start & 63);
                if (endLong >= data.length) break;
                long value = startLong == endLong ? (data[startLong] >>> offset) & mask
                    : ((data[startLong] >>> offset) | (data[endLong] << (64 - offset))) & mask;
                if (value >= states.size()) continue;
                int x = (int) (i % sx), z = (int) ((i / sx) % sz), y = (int) (i / ((long) sx * sz));
                BlockState st = states.get((int) value);
                if (st.isAir()) continue;
                BlockPos p = new BlockPos(ox + x, oy + y, oz + z);
                raw.add(new Block3(p, st));
                minX = Math.min(minX, ox);
                minY = Math.min(minY, oy);
                minZ = Math.min(minZ, oz);
                maxX = Math.max(maxX, ox + sx - 1);
                maxY = Math.max(maxY, oy + sy - 1);
                maxZ = Math.max(maxZ, oz + sz - 1);
            }
        }
        if (raw.isEmpty()) throw new IOException("the litematic has no blocks");
        List<Block3> out = new ArrayList<>(raw.size());
        for (Block3 b : raw) out.add(new Block3(b.pos().offset(-minX, -minY, -minZ), b.state()));
        return new Schematic(name, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, out);
    }

    private static Schematic sponge(String name, CompoundTag root) throws IOException {
        CompoundTag s = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
        int w = s.getShortOr("Width", (short) 0) & 0xFFFF, h = s.getShortOr("Height", (short) 0) & 0xFFFF, l = s.getShortOr("Length", (short) 0) & 0xFFFF;
        CompoundTag palette;
        byte[] data;
        if (s.contains("Blocks")) {
            CompoundTag blocks = s.getCompoundOrEmpty("Blocks");
            palette = blocks.getCompoundOrEmpty("Palette");
            data = blocks.getByteArray("Data").orElse(new byte[0]);
        } else {
            palette = s.getCompoundOrEmpty("Palette");
            data = s.getByteArray("BlockData").orElse(new byte[0]);
        }
        if ((long) w * h * l > MAX_BLOCKS) throw new IOException("the schematic is too big");
        Map<Integer, BlockState> states = new HashMap<>();
        for (String key : palette.keySet()) {
            int id = palette.getIntOr(key, -1);
            try {
                states.put(id, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, key, false).blockState());
            } catch (Exception e) {
                states.put(id, Blocks.AIR.defaultBlockState());
            }
        }
        List<Block3> out = new ArrayList<>();
        int i = 0, index = 0;
        while (i < data.length) {
            int value = 0, shift = 0;
            byte b;
            do {
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && i < data.length);
            BlockState st = states.get(value);
            if (st != null && !st.isAir()) {
                int x = index % w, z = (index / w) % l, y = index / (w * l);
                add(out, new BlockPos(x, y, z), st);
            }
            index++;
        }
        return new Schematic(name, w, h, l, out);
    }

    private static void add(List<Block3> out, BlockPos p, BlockState s) {
        if (!s.isAir() && !s.is(Blocks.STRUCTURE_VOID)) out.add(new Block3(p, s));
    }

    public Schematic rotated(Rotation rot) {
        if (rot == Rotation.NONE) return this;
        List<Block3> out = new ArrayList<>(blocks.size());
        for (Block3 b : blocks) {
            int x = b.pos().getX(), z = b.pos().getZ();
            BlockPos p = switch (rot) {
                case CLOCKWISE_90 -> new BlockPos(sizeZ - 1 - z, b.pos().getY(), x);
                case CLOCKWISE_180 -> new BlockPos(sizeX - 1 - x, b.pos().getY(), sizeZ - 1 - z);
                case COUNTERCLOCKWISE_90 -> new BlockPos(z, b.pos().getY(), sizeX - 1 - x);
                default -> b.pos();
            };
            out.add(new Block3(p, b.state().rotate(rot)));
        }
        boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
        return new Schematic(name, swap ? sizeZ : sizeX, sizeY, swap ? sizeX : sizeZ, out);
    }

    public Map<net.minecraft.world.item.Item, Integer> materials() {
        Map<net.minecraft.world.item.Item, Integer> m = new LinkedHashMap<>();
        for (Block3 b : blocks) {
            net.minecraft.world.item.Item item = b.state().getBlock().asItem();
            if (item != net.minecraft.world.item.Items.AIR) m.merge(item, 1, Integer::sum);
        }
        return m;
    }
}
