package fr.dominion;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class DominionPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey typeKey, oreKey;
    private final Map<String, UUID> playerFaction = new HashMap<>();
    private final Map<UUID, Faction> factions = new HashMap<>();
    private final Map<String, UUID> claims = new HashMap<>();
    private final Map<UUID, Long> finderCooldown = new HashMap<>();
    private final Set<String> generatedOres = new HashSet<>();
    private final Map<String, UUID> caveOwners = new HashMap<>();

    private static final String[] ORES = {"aetherium", "pyrium", "sylvarium"};
    private static final Map<String, Material> ORE_BLOCKS = Map.of(
            "aetherium", Material.AMETHYST_BLOCK,
            "pyrium", Material.REDSTONE_BLOCK,
            "sylvarium", Material.EMERALD_BLOCK);

    private static final class Faction {
        UUID id; String name; UUID leader;
        Set<UUID> members = new HashSet<>();
        Set<UUID> invites = new HashSet<>();
        Faction(UUID id, String name, UUID leader) { this.id=id; this.name=name; this.leader=leader; members.add(leader); }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        typeKey = new NamespacedKey(this, "dominion_item");
        oreKey = new NamespacedKey(this, "dominion_ore");
        loadData();
        registerRecipes();
        Objects.requireNonNull(getCommand("dominion")).setExecutor(this);
        Objects.requireNonNull(getCommand("f")).setExecutor(this);
        Objects.requireNonNull(getCommand("dominion")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("f")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        long period = Math.max(20, getConfig().getLong("armor-effects.check-period-ticks", 40));
        new BukkitRunnable() { public void run() { for (Player p : Bukkit.getOnlinePlayers()) applySetEffects(p); } }.runTaskTimer(this, 20, period);
        getLogger().info("FactionDominion 1.0.0 activé — Paper 1.20.1");
    }

    @Override public void onDisable() { saveData(); }

    private ItemStack tagged(Material material, String name, String type, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + name);
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.GRAY + line);
        meta.setLore(lines);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
    private String itemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }
    private ItemStack gear(String tier, String part) {
        String label = switch (tier) { case "aetherium" -> "Aetherium"; case "pyrium" -> "Pyrium"; case "sylvarium" -> "Sylvarium"; default -> "Sylvarium"; };
        Material mat;
        if (part.equals("helmet")) mat=Material.NETHERITE_HELMET;
        else if (part.equals("chestplate")) mat=Material.NETHERITE_CHESTPLATE;
        else if (part.equals("leggings")) mat=Material.NETHERITE_LEGGINGS;
        else if (part.equals("boots")) mat=Material.NETHERITE_BOOTS;
        else if (part.equals("sword")) mat=Material.NETHERITE_SWORD;
        else if (part.equals("pickaxe")) mat=Material.NETHERITE_PICKAXE;
        else if (part.equals("axe")) mat=Material.NETHERITE_AXE;
        else if (part.equals("shovel")) mat=Material.NETHERITE_SHOVEL;
        else mat=Material.NETHERITE_HOE;
        ItemStack i = tagged(mat, label + " " + partName(part), tier+"_"+part, "Équipement Dominion", "Set : "+label);
        ItemMeta m=i.getItemMeta();
        if (part.equals("sword")) m.addEnchant(Enchantment.DAMAGE_ALL, tier.equals("pyrium")?5:4, true);
        if (part.equals("pickaxe") || part.equals("axe") || part.equals("shovel") || part.equals("hoe")) m.addEnchant(Enchantment.DIG_SPEED, 4, true);
        if (part.equals("helmet") || part.equals("chestplate") || part.equals("leggings") || part.equals("boots")) m.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, tier.equals("aetherium")?4:3, true);
        i.setItemMeta(m); return i;
    }
    private String partName(String p) { return switch(p) {case "helmet"->"Casque";case "chestplate"->"Plastron";case "leggings"->"Jambières";case "boots"->"Bottes";case "sword"->"Épée";case "pickaxe"->"Pioche";case "axe"->"Hache";case "shovel"->"Pelle";default->"Houe";}; }

    private ItemStack make(String id) {
        return switch(id.toLowerCase(Locale.ROOT)) {
            case "aetherium_ingot" -> tagged(Material.AMETHYST_SHARD,"Lingot d'Aetherium",id,"Minerai rare des profondeurs");
            case "pyrium_ingot" -> tagged(Material.BLAZE_POWDER,"Lingot de Pyrium",id,"Métal brûlant");
            case "sylvarium_ingot" -> tagged(Material.EMERALD,"Lingot de Sylvarium",id,"Cristal vivant");
            case "caveblock" -> tagged(Material.LODESTONE,"Cave Block",id,"Protège une zone de 8 blocs", "Objet lié à son propriétaire");
            case "finder_basic" -> tagged(Material.COMPASS,"Unclaim Finder I",id,"Détecte les zones non claimées", "Portée : 8 chunks");
            case "finder_advanced" -> tagged(Material.COMPASS,"Unclaim Finder II",id,"Détecte les zones non claimées", "Portée : 16 chunks");
            case "finder_elite" -> tagged(Material.COMPASS,"Unclaim Finder III",id,"Détecte les zones non claimées", "Portée : 24 chunks");
            case "hammer" -> tagged(Material.NETHERITE_PICKAXE,"Dominion Hammer",id,"Mine une zone 3x3", "Coûte 1 durabilité par bloc cassé");
            case "endglider" -> tagged(Material.ELYTRA,"EndGlider",id,"Ailes de l'End", "Active un bref ralentissement de chute");
            case "farm_helmet" -> tagged(Material.DIAMOND_HELMET,"Casque du Fermier",id,"Armure de farm");
            case "farm_chestplate" -> tagged(Material.DIAMOND_CHESTPLATE,"Plastron du Fermier",id,"Armure de farm");
            case "farm_leggings" -> tagged(Material.DIAMOND_LEGGINGS,"Jambières du Fermier",id,"Armure de farm");
            case "farm_boots" -> tagged(Material.DIAMOND_BOOTS,"Bottes du Fermier",id,"Armure de farm");
            case "vitalis_seed" -> tagged(Material.WHEAT_SEEDS,"Graine de Vitalis",id,"Plante-la sur une terre labourée", "Récolte : graines et éclats Vitalis");
            case "vitalis" -> tagged(Material.GOLDEN_CARROT,"Fruit de Vitalis",id,"Rend 6 points de faim et donne Régénération");
            case "healing_apple" -> tagged(Material.GOLDEN_APPLE,"Pomme du Dominion",id,"Soin renforcé");
            case "speed_orb" -> tagged(Material.ENDER_PEARL,"Orbe de vélocité",id,"Donne Vitesse II pendant 12 secondes");
            case "strength_orb" -> tagged(Material.FIRE_CHARGE,"Orbe de puissance",id,"Donne Force II pendant 8 secondes");
            case "claim_wand" -> tagged(Material.STICK,"Bâton de claim",id,"Clic droit : affiche le claim actuel");
            case "sylvarium_block" -> tagged(Material.EMERALD_BLOCK,"Bloc de Sylvarium",id,"Bloc décoratif");
            default -> { for (String ore:ORES) for(String part:List.of("helmet","chestplate","leggings","boots","sword","pickaxe","axe","shovel","hoe")) if(id.equals(ore+"_"+part)) yield gear(ore,part); yield null; }
        };
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (command.getName().equalsIgnoreCase("dominion")) return dominionCommand(sender,a);
        return factionCommand(sender,a);
    }
    private boolean dominionCommand(CommandSender s, String[] a) {
        if (!s.hasPermission("dominion.admin")) { s.sendMessage(ChatColor.RED+"Permission refusée."); return true; }
        if(a.length==1 && a[0].equalsIgnoreCase("reload")) { reloadConfig(); s.sendMessage(ChatColor.GREEN+"Configuration rechargée."); return true; }
        if(a.length==1 && a[0].equalsIgnoreCase("list")) { s.sendMessage(ChatColor.GOLD+"Objets : caveblock, finder_basic/advanced/elite, hammer, endglider, vitalis_seed, healing_apple, speed_orb, strength_orb, farm_helmet/chestplate/leggings/boots, aetherium/pyrium/sylvarium_ingot et sets complets."); return true; }
        if(a.length<3 || !a[0].equalsIgnoreCase("give")) { s.sendMessage(ChatColor.YELLOW+"/dominion give <joueur> <objet> [quantité]"); s.sendMessage(ChatColor.GRAY+"Objets : /dominion list"); return true; }
        Player target=Bukkit.getPlayerExact(a[1]); if(target==null){s.sendMessage(ChatColor.RED+"Joueur introuvable ou hors ligne.");return true;}
        String id=a[2].toLowerCase(Locale.ROOT); int amount=1; if(a.length>3) try{amount=Math.max(1,Math.min(64,Integer.parseInt(a[3])));}catch(NumberFormatException ignored){}
        if(id.endsWith("_set")) { String tier=id.substring(0,id.length()-4); if(!List.of("aetherium","pyrium","sylvarium","farm").contains(tier)){s.sendMessage(ChatColor.RED+"Set inconnu.");return true;} for(String part:List.of("helmet","chestplate","leggings","boots")){ItemStack piece=tier.equals("farm")?make("farm_"+part):gear(tier,part); target.getInventory().addItem(piece);} s.sendMessage(ChatColor.GREEN+"Set donné à "+target.getName());return true; }
        ItemStack item=make(id); if(item==null){s.sendMessage(ChatColor.RED+"Objet inconnu : "+id);return true;} item.setAmount(amount); target.getInventory().addItem(item); s.sendMessage(ChatColor.GREEN+"Objet donné à "+target.getName()); return true;
    }

    private void registerRecipes(){
        ShapedRecipe seed=new ShapedRecipe(new NamespacedKey(this,"vitalis_seed_recipe"),make("vitalis_seed"));seed.shape(" S ","SES"," S ");seed.setIngredient('S',Material.WHEAT_SEEDS);seed.setIngredient('E',Material.EMERALD);Bukkit.addRecipe(seed);
        ShapedRecipe hammer=new ShapedRecipe(new NamespacedKey(this,"dominion_hammer_recipe"),make("hammer"));hammer.shape("III"," S "," S ");hammer.setIngredient('I',Material.NETHERITE_INGOT);hammer.setIngredient('S',Material.STICK);Bukkit.addRecipe(hammer);
        ShapedRecipe glider=new ShapedRecipe(new NamespacedKey(this,"endglider_recipe"),make("endglider"));glider.shape("M M","MEM","M M");glider.setIngredient('M',Material.PHANTOM_MEMBRANE);glider.setIngredient('E',Material.ELYTRA);Bukkit.addRecipe(glider);
        ShapedRecipe cave=new ShapedRecipe(new NamespacedKey(this,"caveblock_recipe"),make("caveblock"));cave.shape("OOO","OEO","OOO");cave.setIngredient('O',Material.OBSIDIAN);cave.setIngredient('E',Material.ENDER_EYE);Bukkit.addRecipe(cave);
        for(String tier:ORES){ItemStack ingot=make(tier+"_ingot");RecipeChoice.ExactChoice choice=new RecipeChoice.ExactChoice(ingot);
            String[][] patterns={{"III","I I","I I"},{"I I","III","III"},{"III","I I","I I"},{"I I","I I","I I"},{" I "," I "," S "},{"III"," S "," S "},{" II"," SI"," S "},{" I "," S "," S "},{"III"," S "," S "}};
            String[] parts={"helmet","chestplate","leggings","boots","sword","pickaxe","axe","shovel","hoe"};
            for(int i=0;i<parts.length;i++){ItemStack result=gear(tier,parts[i]);ShapedRecipe r=new ShapedRecipe(new NamespacedKey(this,tier+"_"+parts[i]+"_recipe"),result);r.shape(patterns[i]);r.setIngredient('I',choice);if(patterns[i].contains("S"))r.setIngredient('S',Material.STICK);Bukkit.addRecipe(r);}
        }
    }

    private boolean factionCommand(CommandSender s,String[] a) {
        if(!(s instanceof Player p)){s.sendMessage("Commande réservée aux joueurs.");return true;}
        if(a.length==0){p.sendMessage(ChatColor.GOLD+"/f create <nom> | invite <joueur> | join <nom> | leave | disband | info | claim | unclaim | who <joueur>");return true;}
        String sub=a[0].toLowerCase(Locale.ROOT); Faction own=faction(p.getUniqueId());
        switch(sub){
            case "create": { if(own!=null){p.sendMessage(ChatColor.RED+"Tu es déjà dans une faction.");break;} if(a.length<2||a[1].length()>getConfig().getInt("factions.max-name-length",16)){p.sendMessage(ChatColor.RED+"Nom invalide (1 à 16 caractères).");break;} if(findFaction(a[1])!=null){p.sendMessage(ChatColor.RED+"Ce nom existe déjà.");break;} Faction f=new Faction(UUID.randomUUID(),a[1],p.getUniqueId()); factions.put(f.id,f);playerFaction.put(p.getUniqueId().toString(),f.id);p.sendMessage(ChatColor.GREEN+"Faction "+f.name+" créée !");break;}
            case "invite": {if(!isLeader(own,p)){p.sendMessage(ChatColor.RED+"Seul le chef peut inviter.");break;} if(a.length<2){p.sendMessage("/f invite <joueur>");break;} Player target=Bukkit.getPlayerExact(a[1]);if(target==null){p.sendMessage(ChatColor.RED+"Joueur introuvable.");break;}if(faction(target.getUniqueId())!=null){p.sendMessage(ChatColor.RED+"Ce joueur est déjà dans une faction.");break;}own.invites.add(target.getUniqueId());p.sendMessage(ChatColor.GREEN+"Invitation envoyée à "+target.getName());target.sendMessage(ChatColor.GREEN+"Invitation de "+own.name+" : /f join "+own.name);break;}
            case "join": {if(own!=null){p.sendMessage(ChatColor.RED+"Quitte d'abord ta faction.");break;}if(a.length<2){p.sendMessage("/f join <nom>");break;}Faction f=findFaction(a[1]);if(f==null||!f.invites.remove(p.getUniqueId())){p.sendMessage(ChatColor.RED+"Aucune invitation valide.");break;}f.members.add(p.getUniqueId());playerFaction.put(p.getUniqueId().toString(),f.id);broadcast(f,ChatColor.GREEN+p.getName()+" a rejoint la faction.");break;}
            case "leave": {if(own==null){p.sendMessage(ChatColor.RED+"Tu n'as pas de faction.");break;}if(own.leader.equals(p.getUniqueId())){p.sendMessage(ChatColor.RED+"Le chef doit dissoudre la faction.");break;}own.members.remove(p.getUniqueId());playerFaction.remove(p.getUniqueId().toString());broadcast(own,ChatColor.YELLOW+p.getName()+" a quitté la faction.");break;}
            case "disband": {if(!isLeader(own,p)){p.sendMessage(ChatColor.RED+"Seul le chef peut dissoudre.");break;}UUID id=own.id;for(UUID member:own.members)playerFaction.remove(member.toString());claims.entrySet().removeIf(e->e.getValue().equals(id));factions.remove(id);p.sendMessage(ChatColor.GREEN+"Faction dissoute.");break;}
            case "info": {if(own==null){p.sendMessage(ChatColor.RED+"Tu n'as pas de faction.");break;}p.sendMessage(ChatColor.GOLD+"Faction "+own.name+" — "+own.members.size()+" membre(s), "+claims.values().stream().filter(own.id::equals).count()+" claim(s)");for(UUID u:own.members){OfflinePlayer op=Bukkit.getOfflinePlayer(u);p.sendMessage(ChatColor.GRAY+"- "+op.getName()+(u.equals(own.leader)?" (Chef)":""));}break;}
            case "claim": {if(own==null){p.sendMessage(ChatColor.RED+"Tu dois créer/rejoindre une faction.");break;}String key=chunkKey(p.getLocation().getChunk());UUID holder=claims.get(key);if(holder!=null&&!holder.equals(own.id)){p.sendMessage(ChatColor.RED+"Ce chunk est déjà revendiqué.");break;}long count=claims.values().stream().filter(own.id::equals).count();if(holder==null&&count>=getConfig().getInt("factions.claim-limit-per-member",4)*own.members.size()){p.sendMessage(ChatColor.RED+"Limite de claims atteinte.");break;}claims.put(key,own.id);p.sendMessage(ChatColor.GREEN+"Chunk revendiqué pour "+own.name+".");break;}
            case "unclaim": {String key=chunkKey(p.getLocation().getChunk());UUID holder=claims.get(key);if(holder==null){p.sendMessage(ChatColor.RED+"Ce chunk n'est pas claim.");break;}if(own==null||!holder.equals(own.id)){p.sendMessage(ChatColor.RED+"Ce claim ne t'appartient pas.");break;}claims.remove(key);p.sendMessage(ChatColor.GREEN+"Claim retiré.");break;}
            case "who": {if(a.length<2){p.sendMessage("/f who <joueur>");break;}OfflinePlayer op=Bukkit.getOfflinePlayer(a[1]);Faction f=faction(op.getUniqueId());p.sendMessage(f==null?ChatColor.GRAY+"Aucune faction.":ChatColor.GOLD+op.getName()+" appartient à "+f.name);break;}
            default: p.sendMessage(ChatColor.YELLOW+"/f create|invite|join|leave|disband|info|claim|unclaim|who");
        }
        saveData();return true;
    }
    private boolean isLeader(Faction f,Player p){return f!=null&&f.leader.equals(p.getUniqueId());}
    private void broadcast(Faction f,String msg){for(UUID u:f.members){Player p=Bukkit.getPlayer(u);if(p!=null)p.sendMessage(msg);}}
    private Faction faction(UUID u){UUID id=playerFaction.get(u.toString());return id==null?null:factions.get(id);}
    private Faction findFaction(String name){return factions.values().stream().filter(f->f.name.equalsIgnoreCase(name)).findFirst().orElse(null);}
    private String chunkKey(Chunk c){return c.getWorld().getName()+":"+c.getX()+":"+c.getZ();}

    @EventHandler public void onChunkLoad(ChunkLoadEvent e){if(!e.isNewChunk()||!getConfig().getBoolean("world-generation.enabled",true))return;if(getConfig().getBoolean("world-generation.overworld-only",true)&&e.getWorld().getEnvironment()!=World.Environment.NORMAL)return;Chunk c=e.getChunk();Random r=new Random(c.getWorld().getSeed() ^ (((long)c.getX())<<32) ^ c.getZ());for(String ore:ORES){String path="world-generation."+ore;int count=getConfig().getInt(path+".veins-per-chunk",2),size=getConfig().getInt(path+".vein-size",3),min=getConfig().getInt(path+".min-y",-32),max=getConfig().getInt(path+".max-y",32);for(int v=0;v<count;v++){int x=r.nextInt(16),z=r.nextInt(16),y=min+r.nextInt(Math.max(1,max-min+1));for(int n=0;n<size;n++){int bx=Math.max(0,Math.min(15,x+r.nextInt(3)-1)),bz=Math.max(0,Math.min(15,z+r.nextInt(3)-1)),by=Math.max(c.getWorld().getMinHeight(),Math.min(c.getWorld().getMaxHeight()-1,y+r.nextInt(3)-1));Block b=c.getBlock(bx,by,bz);if(b.getType()==Material.STONE||b.getType()==Material.DEEPSLATE){b.setType(ORE_BLOCKS.get(ore),false);generatedOres.add(locKey(b.getLocation()));}}}}}
    private String locKey(Location l){return l.getWorld().getName()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();}
    private Location parseLocation(String raw){try{String[] a=raw.split(":");World w=Bukkit.getWorld(a[0]);return w==null?null:new Location(w,Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]));}catch(Exception ex){return null;}}

    @EventHandler public void onBreak(BlockBreakEvent e){Player p=e.getPlayer();Block b=e.getBlock();String key=locKey(b.getLocation());if(caveOwners.containsKey(key)&&caveOwners.get(key).equals(p.getUniqueId()))caveOwners.remove(key);for(Map.Entry<String,UUID> cb:caveOwners.entrySet()){Location center=parseLocation(cb.getKey());if(center!=null&&center.getWorld().equals(b.getWorld())&&center.distance(b.getLocation())<=getConfig().getInt("cave-block.radius",8)&&!cb.getValue().equals(p.getUniqueId())&&!p.hasPermission("dominion.admin")){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Zone protégée par un Cave Block.");return;}}if(generatedOres.contains(key)){e.setDropItems(false);generatedOres.remove(key);String ore=ORE_BLOCKS.entrySet().stream().filter(x->x.getValue()==b.getType()).map(Map.Entry::getKey).findFirst().orElse("aetherium");ItemStack drop=make(ore+"_ingot");drop.setAmount(1+new Random().nextInt(2));b.getWorld().dropItemNaturally(b.getLocation(),drop);p.giveExp(3);return;}
        UUID holder=claims.get(chunkKey(b.getChunk()));Faction pf=faction(p.getUniqueId());if(getConfig().getBoolean("factions.protect-claims",true)&&holder!=null&&(pf==null||!holder.equals(pf.id))&&!p.hasPermission("dominion.admin")){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Ce territoire appartient à une autre faction.");return;}
        String type=itemType(p.getInventory().getItemInMainHand());if("hammer".equals(type)&&b.getType().isBlock()&&!b.getType().isAir()){hammerBreak(e);}
        if(b.getType()==Material.WHEAT&&b.getBlockData() instanceof Ageable age&&age.getAge()==age.getMaximumAge()&&p.getInventory().getChestplate()!=null&&"farm_chestplate".equals(itemType(p.getInventory().getChestplate()))){b.getWorld().dropItemNaturally(b.getLocation(),new ItemStack(Material.WHEAT_SEEDS,1));}
    }
    private void hammerBreak(BlockBreakEvent e){Player p=e.getPlayer();ItemStack tool=p.getInventory().getItemInMainHand();int radius=Math.max(1,Math.min(2,getConfig().getInt("hammer.radius",1)));Block center=e.getBlock();Vector dir=p.getLocation().getDirection();boolean vertical=Math.abs(dir.getY())>0.7;int broken=0;for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++){if(x==0&&y==0)continue;Block target=vertical?center.getRelative(x,0,y):center.getRelative(x,y,0);if(target.getType().isAir()||target.getType().getHardness()<0||!target.getType().isBlock())continue;UUID holder=claims.get(chunkKey(target.getChunk()));Faction f=faction(p.getUniqueId());if(holder!=null&&(f==null||!holder.equals(f.id))&&!p.hasPermission("dominion.admin"))continue;for(ItemStack drop:target.getDrops(tool,p))target.getWorld().dropItemNaturally(target.getLocation(),drop);target.setType(Material.AIR);broken++;}if(broken>0&&tool.getItemMeta() instanceof Damageable d){int damage=broken*getConfig().getInt("hammer.durability-cost-per-block",1);d.setDamage(Math.min(tool.getType().getMaxDurability()-1,d.getDamage()+damage));tool.setItemMeta((ItemMeta)d);}}

    @EventHandler public void onInteract(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND)return;ItemStack item=e.getItem();String type=itemType(item);if(type==null)return;Player p=e.getPlayer();if(type.equals("vitalis_seed")&&e.getAction().isRightClick()&&e.getClickedBlock()!=null&&e.getClickedBlock().getType()==Material.FARMLAND){Block plant=e.getClickedBlock().getRelative(BlockFace.UP);if(plant.getType().isAir()){plant.setType(Material.WHEAT);if(plant.getBlockData() instanceof Ageable age){age.setAge(0);plant.setBlockData(age);}consume(p,item);p.sendMessage(ChatColor.GREEN+"Graine de Vitalis plantée.");}e.setCancelled(true);}
        else if(type.startsWith("finder_")&&e.getAction().isRightClick()){long now=System.currentTimeMillis(),last=finderCooldown.getOrDefault(p.getUniqueId(),0L);long cooldown=getConfig().getLong("unclaim-finder.cooldown-seconds",8400)*1000L;if(now-last<cooldown){p.sendMessage(ChatColor.RED+"Recharge : "+((cooldown-(now-last))/1000)+" secondes.");return;}int radius=type.equals("finder_basic")?8:type.equals("finder_advanced")?16:24;int found=0;Chunk origin=p.getLocation().getChunk();for(int x=-radius;x<=radius&&found<5;x++)for(int z=-radius;z<=radius&&found<5;z++){String ck=p.getWorld().getName()+":"+(origin.getX()+x)+":"+(origin.getZ()+z);if(!claims.containsKey(ck)){p.sendMessage(ChatColor.GREEN+"Chunk non claimé détecté : "+(origin.getX()+x)+", "+(origin.getZ()+z));found++;}}if(found==0)p.sendMessage(ChatColor.RED+"Aucun chunk libre détecté dans cette zone.");finderCooldown.put(p.getUniqueId(),now);p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,1,1.4f);}
        else if(type.equals("endglider")&&e.getAction().isRightClick()){p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,getConfig().getInt("endglider.slow-falling-seconds",3)*20,0));p.playSound(p.getLocation(),Sound.ITEM_ELYTRA_FLYING,1,1.2f);}
        else if(type.equals("speed_orb")&&e.getAction().isRightClick()){p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,240,1));consume(p,item);}
        else if(type.equals("strength_orb")&&e.getAction().isRightClick()){p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE,160,1));consume(p,item);}
        else if(type.equals("healing_apple")&&e.getAction().isRightClick()){p.setHealth(Math.min(Objects.requireNonNull(p.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue(),p.getHealth()+8));p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,1));consume(p,item);}
        else if(type.equals("vitalis")&&e.getAction().isRightClick()){p.setFoodLevel(Math.min(20,p.getFoodLevel()+6));p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,160,1));consume(p,item);}
    }
    private void consume(Player p,ItemStack i){if(p.getGameMode()!=GameMode.CREATIVE)i.setAmount(i.getAmount()-1);}

    @EventHandler public void onEntityDamage(EntityDamageByEntityEvent e){if(!(e.getDamager() instanceof Player a)||!(e.getEntity() instanceof Player b))return;Faction fa=faction(a.getUniqueId()),fb=faction(b.getUniqueId());if(fa!=null&&fb!=null&&fa.id.equals(fb.id)){e.setCancelled(true);}}
    @EventHandler public void onPlace(BlockPlaceEvent e){String type=itemType(e.getItemInHand());Player p=e.getPlayer();for(Map.Entry<String,UUID> cb:caveOwners.entrySet()){Location center=parseLocation(cb.getKey());if(center!=null&&center.getWorld().equals(e.getBlockPlaced().getWorld())&&center.distance(e.getBlockPlaced().getLocation())<=getConfig().getInt("cave-block.radius",8)&&!cb.getValue().equals(p.getUniqueId())&&!p.hasPermission("dominion.admin")){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Zone protégée par un Cave Block.");return;}}if(type==null)return;if(type.equals("caveblock")){String k=locKey(e.getBlockPlaced().getLocation());caveOwners.put(k,p.getUniqueId());p.sendMessage(ChatColor.AQUA+"Cave Block activé. Zone de protection visuelle : "+getConfig().getInt("cave-block.radius",8)+" blocs.");}}
    @EventHandler public void onJoin(PlayerJoinEvent e){applySetEffects(e.getPlayer());}
    private void applySetEffects(Player p){String[] armor={"helmet","chestplate","leggings","boots"};Map<String,Integer> count=new HashMap<>();for(String tier:List.of("aetherium","pyrium","sylvarium","farm")){int n=0;for(int i=0;i<4;i++){ItemStack piece=switch(i){case 0->p.getInventory().getHelmet();case 1->p.getInventory().getChestplate();case 2->p.getInventory().getLeggings();default->p.getInventory().getBoots();};if((tier+"_"+armor[i]).equals(itemType(piece)))n++;}count.put(tier,n);}if(count.get("aetherium")==4)p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,80,1,true,false));if(count.get("pyrium")==4)p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE,80,0,true,false));if(count.get("sylvarium")==4)p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,80,0,true,false));if(count.get("farm")==4){p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING,80,0,true,false));p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,80,0,true,false));}}

    private void loadData(){if(!getDataFolder().exists())getDataFolder().mkdirs();org.bukkit.configuration.file.YamlConfiguration y=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(),"data.yml"));generatedOres.addAll(y.getStringList("generated-ores"));org.bukkit.configuration.ConfigurationSection caves=y.getConfigurationSection("cave-blocks");if(caves!=null)for(String k:caves.getKeys(false)){try{caveOwners.put(k,UUID.fromString(caves.getString(k)));}catch(Exception ignored){}}org.bukkit.configuration.ConfigurationSection fs=y.getConfigurationSection("factions");if(fs!=null)for(String idText:fs.getKeys(false)){try{String base="factions."+idText;UUID id=UUID.fromString(idText),leader=UUID.fromString(y.getString(base+".leader"));Faction f=new Faction(id,y.getString(base+".name"),leader);for(String u:y.getStringList(base+".members"))f.members.add(UUID.fromString(u));for(String u:y.getStringList(base+".invites"))f.invites.add(UUID.fromString(u));factions.put(id,f);for(UUID u:f.members)playerFaction.put(u.toString(),id);}catch(Exception ignored){}}org.bukkit.configuration.ConfigurationSection cs=y.getConfigurationSection("claims");if(cs!=null)for(String k:cs.getKeys(false)){try{claims.put(k,UUID.fromString(cs.getString(k)));}catch(Exception ignored){}}}
    private void saveData(){org.bukkit.configuration.file.YamlConfiguration y=new org.bukkit.configuration.file.YamlConfiguration();y.set("generated-ores",new ArrayList<>(generatedOres));for(Map.Entry<String,UUID> cb:caveOwners.entrySet())y.set("cave-blocks."+cb.getKey(),cb.getValue().toString());for(Faction f:factions.values()){String b="factions."+f.id;y.set(b+".id",f.id.toString());y.set(b+".name",f.name);y.set(b+".leader",f.leader.toString());y.set(b+".members",f.members.stream().map(UUID::toString).toList());y.set(b+".invites",f.invites.stream().map(UUID::toString).toList());}for(Map.Entry<String,UUID> e:claims.entrySet())y.set("claims."+e.getKey(),e.getValue().toString());try{y.save(new java.io.File(getDataFolder(),"data.yml"));}catch(java.io.IOException ex){getLogger().severe("Impossible de sauvegarder data.yml : "+ex.getMessage());}}

    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(c.getName().equalsIgnoreCase("f")){if(args.length==1)return List.of("create","invite","join","leave","disband","info","claim","unclaim","who");if(args.length==2&&args[0].equalsIgnoreCase("invite"))return null;}if(c.getName().equalsIgnoreCase("dominion")){if(args.length==1)return List.of("give","list","reload");if(args.length==3)return List.of("caveblock","finder_basic","finder_advanced","finder_elite","hammer","endglider","vitalis_seed","healing_apple","speed_orb","strength_orb","aetherium_set","pyrium_set","sylvarium_set","farm_set");}return Collections.emptyList();}
}
