import java.util.ArrayList;
import java.util.List;

import se.miun.distsys.GroupCommuncation;
import se.miun.distsys.listeners.Listeners;
import se.miun.models.User;

abstract public class BaseProgram implements Listeners {

	protected GroupCommuncation gc = null; 
  List<BotProgram> bots = new ArrayList<>();
  private int botInstances = 0;
  protected Logger logger = new Logger();


  public void initGroupCommunication() {
    gc = new GroupCommuncation();
    gc.setListeners(this);
    System.out.println("Group Communcation Started");
  }
  protected String getUserInfo(User user) 
  {
    //return "userId:" + user.userId + ", adress:" + user.address;
    return "userId:" + user.userId;
  }
  protected void addBots() { 
		for(int i = 0; i < botInstances; i++, addBot()); 
  }
  protected void terminateBots() {
    for(int i = bots.size(); i > 0; removeBot(), i-- );
  }


  protected void addBot() {
     bots.add(new BotProgram());
     botInstances++;
  }
  protected void removeBot() {
    if(bots.size() > 0) {
      bots.get(bots.size() - 1).botIsRunning = false;
      bots.remove(bots.size() - 1);
    }
  }
}