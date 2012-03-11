package org.tinkernut.apririce.commands;

import org.tinkernut.apririce.textUtils.Parser;
import jerklib.events.MessageEvent;

public class NickServCommand implements Command {
	String params;
	MessageEvent me;

	public void init(String params, MessageEvent me) {
		this.params = params;
		this.me = me;
	}

	public void run() {
	}

	public void execPriv(String user) {
		if (params.startsWith("identify")) {
			me.getSession().sayPrivate("nickserv", "identify " + Parser.stripAguments(params));
		}else if (params.startsWith("register")) {
			me.getSession().sayPrivate("nickserv", "register " + Parser.stripAguments(params));
		}else if (params.startsWith("group")) {
			me.getSession().sayPrivate("nickserv", "group " + Parser.stripAguments(params));
		}else {
			System.out.println("Invalid arguments.");
		}
	}
}
