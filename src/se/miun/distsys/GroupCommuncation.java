package se.miun.distsys;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;

import javax.annotation.PreDestroy;

import javafx.util.Pair;
import se.miun.distsys.listeners.Listeners;
import se.miun.distsys.listeners.MessageAccepted;
import se.miun.distsys.listeners.MessageOutOfOrder;
import se.miun.distsys.messages.ChatMessage;
import se.miun.distsys.messages.LoginMessage;
import se.miun.distsys.messages.LogoutMessage;
import se.miun.distsys.messages.Message;
import se.miun.distsys.messages.MessageSerializer;
import se.miun.distsys.messages.SendLoginMessage;
import se.miun.models.Constants;
import se.miun.models.User;




public class GroupCommuncation implements MessageAccepted, MessageOutOfOrder {
	private int datagramSocketPort = Constants.groupChatPort;
	private int serverPort = Constants.serverPort;

	DatagramSocket datagramSocket = null;	
	User user;
	boolean runGroupCommuncation = true;	
	MessageSerializer messageSerializer = new MessageSerializer();
	//Listeners
	Listeners listeners = null;

	VectorClockService vectorClockService = new VectorClockService(this, this);
	
	public GroupCommuncation()
	{
		try {
			runGroupCommuncation = true;				
			datagramSocket = new MulticastSocket(datagramSocketPort);
			RecieveThread rt = new RecieveThread();
			rt.start();	
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void shutdown() {
			runGroupCommuncation = false;	
			logout();	
	}
	

	class RecieveThread extends Thread {
 
		@Override
		public void run() {
			byte[] buffer = new byte[65536];		
			DatagramPacket recievePacket = new DatagramPacket(buffer, buffer.length);

			login();

			while(runGroupCommuncation) {
				try {
					datagramSocket.receive(recievePacket);									
					byte[] packetData = recievePacket.getData();	
                                        
					Message recievedMessage = messageSerializer.deserializeMessage(packetData);	
					
					handleMessage(recievedMessage);

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		private void handleMessage (Message message) {

			if(message instanceof LoginMessage) {
				if(!isSelf(message.user)) { // dont login yourself, youre already logged in :)
					System.out.println(user.userId + "added" + message.user.userId);
					addUser(message.user);
					listeners.onUserLogin(message.user);
					broadcastMessage(new SendLoginMessage(user));
				}
			}
			else if(message instanceof SendLoginMessage) {
				// if you dont contain the user and its not yourself :)
				if(!user.vectorClock.containsKey(message.user.userId) && !isSelf(message.user)) {
					addUser(message.user);
					listeners.onSendLoginListener(message.user);
				}
			}

			// to prohibit unregistred user to send messages
			else if(user.vectorClock.containsKey(message.user.userId)) {
		
				if(message instanceof LogoutMessage) {
					removeUser(message.user);
					listeners.onUserLogout(message.user);
				}
				
				else if(message instanceof ChatMessage) {
					vectorClockService.addMessage((ChatMessage) message);
					vectorClockService.checkChatMessages(user);
				}
				else {
					System.out.println("unknown message type");
				}
			}
		}
	}

	public void broadcastChatMessage(String chat) {
		ChatMessage chatMessage = new ChatMessage(user, chat);
		VectorClockService.incremenetMessageIndex(chatMessage);
		broadcastMessage(chatMessage);
		user.vectorClock.put(user.userId, user.vectorClock.get(user.userId) - 1);
	}

	public <T extends Message> void broadcastMessage(T message) 
    {
		try {
			sendMessage(message, InetAddress.getByName("255.255.255.255"));
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}
	public <T extends Message> void sendMessage(T message, InetAddress inetAddress)
	{
		try {
			byte[] sendData = messageSerializer.serializeMessage(message);
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, datagramSocketPort);
			datagramSocket.send(sendPacket);
		} catch (Exception e) { 
			e.printStackTrace();
		}
	}
	private void login() 
	{	
		initUser();
		broadcastMessage(new LoginMessage(user));
	}

	@PreDestroy
	private void logout()
	{
		System.out.println("broadcastning logoutMessage before exiting");
		broadcastMessage(new LogoutMessage(user));
	}
	public void setListeners(Listeners listeners)
	{
		this.listeners = listeners;
	}

	private void initUser() { 
		user = new User(datagramSocket.getLocalSocketAddress(), datagramSocket.getInetAddress()); 
		listeners.onUserLogin(user);
	}

	private void addUser(User user)
	{
		GroupCommuncation.this.user.vectorClock.put(user.userId, user.vectorClock.get(user.userId));
	}
	
	private void removeUser(User user)
	{
		GroupCommuncation.this.user.vectorClock.remove(user.userId);
		vectorClockService.removeUser(user);
	}

	private boolean isSelf(User user) { return user.userId == GroupCommuncation.this.user.userId; }

	public User getUser() { return this.user; }

	@Override
	public void onMessageAccepted(ChatMessage chatMessage) {
		//this.user.vectorClock = chatMessage.user.vectorClock; 
		this.user.vectorClock.put(chatMessage.user.userId, this.user.vectorClock.get(chatMessage.user.userId) + 1);
		listeners.onIncomingChatMessage(chatMessage);
		// check again
		vectorClockService.checkChatMessages(user);
	}

	@Override
	public void onOutOfOrder(ChatMessage chatMessage) {
		listeners.onOutOfOrder(chatMessage);
	}
}
