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
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.entity.CraftPlayer;

public class ChunkManager extends JavaPlugin implements Runnable {
  Logger log = Logger.getLogger("Minecraft");
  private int taskID = 0;
  Method M = null;
  HashMap<String, Set<ChunkCoordIntPair>> waitinglist = new HashMap<String, Set<ChunkCoordIntPair>>();

  public void Info(String m){log.info("[ChunkManager] "+m);}

  @Override
  public void onDisable() {
    PluginDescriptionFile pdfFile = getDescription();
    if (taskID != 0){getServer().getScheduler().cancelTask(taskID);}
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

  public void run() {
    Set<ChunkCoordIntPair> waitset = null;
    Player[] players = getServer().getOnlinePlayers();
    for (Player P : players){
      EntityPlayer E = ((CraftPlayer)P).getHandle();
      if (waitinglist.get(P.getName()) == null){waitinglist.put(P.getName(), new HashSet<ChunkCoordIntPair>());}
      waitset = waitinglist.get(P.getName());
      //if this player has chunks waiting, remove them and add to our waiting list
      if (E.chunkCoordIntPairQueue.size() > 0){
        for (Object Pair : E.chunkCoordIntPairQueue){waitset.add((ChunkCoordIntPair)Pair);}
        E.chunkCoordIntPairQueue.clear();
      }
      //send at most one chunk this player needs
      if (!waitset.isEmpty()) {
        ChunkCoordIntPair willsend = null;
        int x = (int) E.locX >> 4;
        int z = (int) E.locZ >> 4;
        if (E.motX != 0){x += ((E.motX * 5) / 16);}
        if (E.motZ != 0){z += ((E.motZ * 5) / 16);}
        //find the best chunk to send
        for (ChunkCoordIntPair Pair : waitset){
          if (willsend == null){willsend = Pair;}
          if (Math.max(Math.abs(willsend.x - x), Math.abs(willsend.z - z)) > Math.max(Math.abs(Pair.x - x), Math.abs(Pair.z - z))){
            willsend = Pair;
          }
        }
        //remove from waiting list
        waitset.remove(willsend);
        //only send if not too far away
        int distance = Math.max(Math.abs(willsend.x - x), Math.abs(willsend.z - z));
        if (distance <= 10){
          WorldServer worldserver = E.b.getWorldServer(E.dimension);
          E.netServerHandler.sendPacket(new Packet51MapChunk(willsend.x * 16, 0, willsend.z * 16, 16, worldserver.height, 16, worldserver));
          @SuppressWarnings("rawtypes")
          List list = worldserver.getTileEntities(willsend.x * 16, 0, willsend.z * 16, willsend.x * 16 + 16, worldserver.height, willsend.z * 16 + 16);
          for (int j = 0; j < list.size(); ++j) {
            try{
              M.invoke(E, (TileEntity)list.get(j));
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
}
