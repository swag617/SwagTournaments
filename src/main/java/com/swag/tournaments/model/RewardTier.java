package com.swag.tournaments.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class RewardTier {

    private final int place;
    private final double money;
    private final List<String> commands;
    private final List<ItemStack> items;

    public RewardTier(int place, double money, List<String> commands, List<ItemStack> items) {
        this.place = place;
        this.money = money;
        this.commands = List.copyOf(commands);
        this.items = List.copyOf(items);
    }

    public int getPlace() {
        return place;
    }

    public double getMoney() {
        return money;
    }

    public List<String> getCommands() {
        return commands;
    }

    public List<ItemStack> getItems() {
        return items;
    }
}
