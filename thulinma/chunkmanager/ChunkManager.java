/**
 * ChunkManager: ChunkManager.java
 */
package thulinma.chunkmanager;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.minecraft.server.ChunkCoordIntPair;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Packet51MapChunk;
import net.minecraft.server.TileEntity;
import net.minecraft.server.WorldServer;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.entity.CraftPlayer;

public class ChunkManager extends JavaPlugin implements Runnable {
  Logger log = Logger.getLogger("Minecraft");
  private int taskID = 0;
  Method M = null;
  HashMap<Player, Set<ChunkCoordIntPair>> waitinglist = new HashMap<Player, Set<ChunkCoordIntPair>>();
  HashMap<Player, World> worldlist = new HashMap<Player, World>();

  public void Info(String m){log.info("[ChunkManager] "+m);}

  @SuppressWarnings("unchecked")
  @Override
  public void onDisable() {
    PluginDescriptionFile pdfFile = getDescription();
    if (taskID != 0){getServer().getScheduler().cancelTask(taskID);}
    //restore chunkCoordIntPairQueue contents for all online players
    Player[] players = getServer().getOnlinePlayers();
    for (Player P : players){
      if (waitinglist.get(P) != null && !waitinglist.get(P).isEmpty()){
        EntityPlayer E = ((CraftPlayer)P).getHandle();
        E.chunkCoordIntPairQueue.addAll(waitinglist.get(P));
      }
    }
    Info(pdfFile.getVersion()+" disabled.");
  }

  @Override
  public void onEnable() {
    PluginDescriptionFile pdfFile = getDescription();
    try{
      M = net.minecraft.server.EntityPlayer.class.getDeclaredMethod("a", TileEntity.class);
      M.setAccessible(true);
    } catch (Exception e){
      Info("It looks like this CB server is not compatible - sorry :-(");
      return;
    }
    taskID = getServer().getScheduler().scheduleSyncRepeatingTask(this, this, 2, 2);
    Info(pdfFile.getVersion()+" enabled.");
  }

  @SuppressWarnings("unchecked")
  public void run() {
    Set<ChunkCoordIntPair> waitset = null;
    Set<ChunkCoordIntPair> removeset = new HashSet<ChunkCoordIntPair>();
    Player[] players = getServer().getOnlinePlayers();
    for (Player P : players){
      //get or create waiting list
      if (waitinglist.get(P) == null){waitinglist.put(P, new HashSet<ChunkCoordIntPair>());}
      waitset = waitinglist.get(P);
      //clear the waiting list if the player changed worlds
      if (P.getWorld() != worldlist.get(P)){
        worldlist.put(P, P.getWorld());
        waitset.clear();
      }
      EntityPlayer E = ((CraftPlayer)P).getHandle();
      //if this player has chunks waiting, remove them and add to our waiting list
      if (E.chunkCoordIntPairQueue.size() > 0){
        waitset.addAll(E.chunkCoordIntPairQueue);
        E.chunkCoordIntPairQueue.clear();
      }
      //send at most one chunk this player needs
      if (!waitset.isEmpty()) {
        ChunkCoordIntPair willsend = null;
        int x = (int) (E.locX + E.motX * 10) >> 4;
        int z = (int) (E.locZ + E.motZ * 10) >> 4;
        //find the best chunk to send
        int distA = 99;
        int distB = 0;
        for (ChunkCoordIntPair Pair : waitset){
          if (willsend == null){willsend = Pair;}
          distB = Math.max(Math.abs(Pair.x - x), Math.abs(Pair.z - z));
          if (distA > distB){
            willsend = Pair; distA = distB;
          }else{
            if (distB > 25){removeset.add(Pair);}
          }
        }
        //remove from waiting list if too far
        if (removeset.size() > 0){for (ChunkCoordIntPair Pair : removeset){waitset.remove(Pair);}}
        //remove current from waiting list
        waitset.remove(willsend);
        //only send if not too far away
        if (distA <= 10){
          try{
            WorldServer worldserver = E.server.getWorldServer(E.dimension);
            E.netServerHandler.sendPacket(new Packet51MapChunk(willsend.x * 16, 0, willsend.z * 16, 16, worldserver.height, 16, worldserver));
            @SuppressWarnings("rawtypes")
            List list = worldserver.getTileEntities(willsend.x * 16, 0, willsend.z * 16, willsend.x * 16 + 16, worldserver.height, willsend.z * 16 + 16);
            for (int j = 0; j < list.size(); ++j) {
              M.invoke(E, (TileEntity)list.get(j));
            }
          } catch (Exception e){
            Info("It looks like this CB server is not compatible - sorry :-(");
            onDisable();
            return;
          }
        }
      }
    }
  }
}
