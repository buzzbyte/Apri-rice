// TODO: Make threaded

package org.tinkernut.apririce;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.tinkernut.apririce.commands.AnnounceCommand;
import org.tinkernut.apririce.commands.Command;
import org.tinkernut.apririce.commands.DefineCommand;
import org.tinkernut.apririce.commands.HelpCommand;
import org.tinkernut.apririce.commands.LogCommand;
import org.tinkernut.apririce.commands.NickServCommand;
import org.tinkernut.apririce.commands.QuitCommand;
import org.tinkernut.apririce.commands.UserCommand;
import org.tinkernut.apririce.textUtils.Parser;
import jerklib.ConnectionManager;
import jerklib.Profile;
import jerklib.events.IRCEvent;
import jerklib.events.JoinCompleteEvent;
import jerklib.events.JoinEvent;
import jerklib.events.MessageEvent;
import jerklib.events.QuitEvent;
import jerklib.events.IRCEvent.Type;
import jerklib.listeners.IRCEventListener;

public class Bot implements IRCEventListener, Runnable {
	/**
	 * Globals
	 */
	public String channelName;
	public boolean isLogging = false;
	private final String CMD_START = "|";
	private String ircServer;
	public ConnectionManager con;
	private HashMap<String, Command> commandsMap;
	private BufferedWriter bLogWriter;
	private String botName;
	public LinkedList<User> userList;
	//Global instance commands
	private Command announceCommand;

	ExecutorService privateExecutorService = Executors.newCachedThreadPool();
	ExecutorService publicExecutorService = Executors.newCachedThreadPool();	

	/**
	 * Class constructor
	 */
	public Bot(String server, String channel, String botName) {
		// Initialize globals		
		commandsMap = new HashMap<String, Command>();

		announceCommand = new AnnounceCommand();

		userList = new LinkedList<User>();

		ircServer = server;
		channelName = channel;
		this.botName = botName;

		// TODO: Create storage
		// Bot profile (nick)
		con = new ConnectionManager(new Profile(this.botName));
	}

	//Thread start
	public void run() {
		// Request Connection to server
		try {
			con.requestConnection(ircServer).addIRCEventListener(this);
		}
		catch (Exception e) {
			System.out.println("Failed to connect to channel. Exiting.");
			return;
		}
	}
	/**
	 * Event handler
	 */
	public void receiveEvent(IRCEvent e) {
		System.out.println(e.getRawEventData());

		Type type = e.getType();
		// Connection to server successful
		if (type == Type.CONNECT_COMPLETE) {
			e.getSession().join(channelName);

			// Connection to channel successful
		} else if (type == Type.JOIN_COMPLETE) {
			JoinCompleteEvent jce = (JoinCompleteEvent) e;

			BufferedReader bReader;
			String userString = "";

			try {
				bReader = new BufferedReader(new FileReader("users.txt"));
				String s;
				while ((s = bReader.readLine()) != null) {
					userString += s;
				}
				bReader.close();

				bReader = new BufferedReader(new FileReader("users.txt"));
				if (userString != "") {
					s = bReader.readLine();
					
					jce.getChannel().say(s);
					
					String nick = s.substring(0, s.indexOf(' '));
					int warnings = Integer.parseInt(s.substring(nick.length() + 1, nick.length() + 2));
//					Rank rank;
					
					jce.getChannel().say(nick);
					jce.getChannel().say(Integer.toString(warnings));
				}

				bReader.close();
				bReader = null;
			} catch (FileNotFoundException e3) {
				System.out.println("Error: Could not lock users.txt");
			} catch (IOException e1) {
				System.out.println("Error: File is inaccessible.");
			}

			PrintWriter PWriter = null;
			Object[] nicks = jce.getChannel().getNicks().toArray();	

			//Add all users in channel as new User
			for (int i = 0; i < jce.getChannel().getNicks().toArray().length; i++) {
				if (!userList.contains(new User(nicks[i].toString()).getNick())) {
					try {
						PWriter = new PrintWriter(new FileWriter("users.txt", true));
						bReader = new BufferedReader(new FileReader("users.txt"));

						if (!userString.toLowerCase().contains(nicks[i].toString().toLowerCase())) {							
							PWriter.println(nicks[i].toString() + " 0" + " standard");
							jce.getChannel().say("New user detected.");
						}

						PWriter.close();
						bReader.close();
					} catch (IOException e1) {
						System.out.println("users.txt is inaccessible.");
					} catch (NullPointerException e2) {
						PWriter.println(nicks[i].toString());
					}

					userList.add(new User(nicks[i].toString().toLowerCase()));					
				}
			}

			// User successfuly joins channel
		} else if (type == Type.JOIN) {
			JoinEvent je = (JoinEvent) e;
			// Create User instance variable for each new user in userList
			if (!userList.contains(new User(je.getNick()))) {
				userList.add(new User(je.getNick().toLowerCase()));
			}
			// User successfuly leaves channel
		} else if (type == Type.QUIT) {
			QuitEvent qe = (QuitEvent) e;

			// Message successfuly recieved in channel
		} else if (type == Type.CHANNEL_MESSAGE) {
			// Logging.
			if (isLogging) {
				try {
					bLogWriter = new BufferedWriter(new FileWriter("log.txt", true));
					bLogWriter.write(e.getRawEventData());
					bLogWriter.newLine();
					bLogWriter.close();
				} catch (IOException e1) {
					System.out.println("Error. Could not open log file.");
				}
			}

			MessageEvent me = (MessageEvent) e;
			// Check and execute any commands
			if (me.getMessage().startsWith(CMD_START)) {
				String commandString = Parser.stripCommand(me.getMessage());

				//Local instance commands
				Command helpCommand = new HelpCommand();
				Command defineCommand = new DefineCommand();
				Command logCommand = new LogCommand();
				Command quitCommand = new QuitCommand();
				Command userCommand = new UserCommand();

				//Put identifier and associated command
				commandsMap.put("help", helpCommand);
				commandsMap.put("define", defineCommand);
				commandsMap.put("announce", announceCommand);
				commandsMap.put("log", logCommand);
				commandsMap.put("quit", quitCommand);
				commandsMap.put("user", userCommand);

				if (commandsMap.containsKey(commandString)) {
					commandsMap.get(commandString).init(Parser.stripArguments(me.getMessage()), me, this);

					publicExecutorService.execute(commandsMap.get(commandString));
				}else {
					me.getChannel().say("Not a command.");
				}
			}
			// Private message successfuly recieved
		} else if (type == Type.PRIVATE_MESSAGE) {
			// Logging.
			if (isLogging) {
				try {
					bLogWriter = new BufferedWriter(new FileWriter("log.txt", true));
					bLogWriter.write(e.getRawEventData());
					bLogWriter.newLine();
					bLogWriter.close();
				} catch (IOException e1) {
					System.out.println("Error. Could not open log file.");
				}
			}

			MessageEvent me = (MessageEvent) e;
			if (me.getMessage().startsWith(CMD_START)) {			
				// Check and execute any commands
				String commandString = Parser.stripCommand(me.getMessage());

				//Local instance commands
				Command helpCommand = new HelpCommand();
				Command nickServCommand = new NickServCommand();
				Command logCommand = new LogCommand();
				Command quitCommand = new QuitCommand();
				Command userCommand = new UserCommand();

				//Put identifier and associated command
				commandsMap.put("nickserv", nickServCommand);
				commandsMap.put("log", logCommand);
				commandsMap.put("help", helpCommand);
				commandsMap.put("quit", quitCommand);
				commandsMap.put("user", userCommand);

				if (commandsMap.containsKey(commandString)) {
					commandsMap.get(commandString).initPriv(Parser.stripArguments(me.getMessage()), me, this, userList.get(userList.indexOf(new User(me.getNick().toLowerCase()))));

					privateExecutorService.execute(commandsMap.get(commandString));
				}
				else {
					me.getSession().sayPrivate(me.getNick(), "Not a command.");
				}
			}
		}
	}
}