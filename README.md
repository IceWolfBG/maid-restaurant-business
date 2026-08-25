# Maid Restaurant: Now Open (女仆餐厅：营业中)

A Minecraft Forge 1.20.1 addon mod that bridges **Touhou Little Maid** (车万女仆), **Maid Restaurant** (女仆餐厅), and **Order to Cook** (下单了) to enable fully automated restaurant management by maids.

## Features

- **Auto Order Acceptance** (4级解锁): Automatically selects orders based on available ingredients and order value
- **Auto Cooking & Prep** (1级解锁): Chef maids automatically cook required food and fetch prepared food from containers
- **Auto Packaging** (0级解锁): Waiter maids automatically pack/plate orders at the countertop
- **Auto Delivery** (0级解锁): Waiter maids deliver plates to customer seats
- **Auto Dishwashing** (2级解锁): Waiter maids collect dirty plates and wash them
- **Level-based Progression**: Automation features unlock gradually as the Order Machine levels up
- **Configurable**: All features can be toggled in the config file (supports Configured mod for in-game editing)

## Dependencies

- Minecraft 1.20.1
- Forge 47+
- [Touhou Little Maid](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid) 1.5.3+
- [Maid Restaurant](https://www.mcmod.cn/class/24640.html) 0.2.9+
- [Order to Cook](https://www.modrinth.com/mod/order-to-cook) 1.0.0+

## How to Use

1. Place an Order Machine (打单机) and a Takeout Box (操作台/打包台) nearby
2. Place an Item Frame (展示框) within 3 blocks of the Order Machine
3. Put a Restaurant Menu (餐厅菜单) in the Item Frame to activate automation
4. Assign chef maids and waiter maids to work near the restaurant
5. As the Order Machine levels up, more automation features unlock

## Configuration

Config file: `config/maid_restaurant_business-common.toml`

- `autoAccept`: Enable auto order acceptance (default: false)
- `autoPack`: Enable auto packaging (default: true)
- `waiterDeliver`: Enable waiter auto delivery (default: true)
- `autoWash`: Enable auto dishwashing (default: true)
- `levelBasedProgression`: Enable level-based feature unlocking (default: true)
- `acceptDelay`: Auto accept delay in ticks (default: 200 = 10 seconds)

## License

MIT License

## Credits

- Developed by IceWolf
- Inspired by the Touhou Little Maid and Maid Restaurant modding communities
