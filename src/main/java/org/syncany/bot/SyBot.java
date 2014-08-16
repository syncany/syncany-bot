/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.bot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.service.IssueService;
import org.jibble.pircbot.PircBot;

public class SyBot extends PircBot {
	private static final String DEFAULT_CONFIG_FILE = "sybot.properties";

	private static Pattern REQ_ISSUE_PATTERN = Pattern.compile("^\\#(\\d{1,10})");
	private static int REQ_ISSUE_PATTERN_GROUP_ISSUE = 1;
	
	private String name;
	private String server;
	private String channel;
	private String identify; // password	
	private String logdir;
	
	public static void main(String[] args) throws Exception {
		String configFile = (args.length > 0) ? args[0] : DEFAULT_CONFIG_FILE;		
		new SyBot(configFile).start();
	}
	
	public SyBot(String configFile) throws Exception {
		loadConfig(configFile);
	}

	private void loadConfig(String configFile) throws Exception {
		Properties config = new Properties();		
		config.load(new FileInputStream(configFile));
		
		this.name = config.getProperty("name");
		this.server = config.getProperty("server");
		this.channel = config.getProperty("channel");
		this.identify = config.getProperty("identify");
		this.logdir = config.getProperty("logdir", "logs/");
		
		if (name == null || server == null || channel == null) {
			throw new Exception("Invalid config. Properties 'name', 'server' and 'channel' must be set.");
		}
		
		new File(logdir).mkdirs();
	}

	public void start() throws Exception {
		connectAndJoin();
	}
	
	public void connectAndJoin() throws Exception {
		setName(name);
		setLogin(name);
		setFinger(name);
		setVersion(name);		
		setAutoNickChange(true);
		
		connect(server);
		
		if (identify != null) {
			identify(identify);
		}
		
		joinChannel(channel);
	}

	@Override
	protected void onDisconnect() {
		try {
			Thread.sleep(1000);
			connectAndJoin();
		}
		catch (Exception e) {
			// Nothing
		}
	}
	
	@Override
	public void onMessage(String channel, String sender, String login, String hostname, String message) {		
		appendToLog(String.format("<%s> %s", shortenStr(sender, 50), shortenStr(message, 1024)));		

		Matcher printIssueMatcher = REQ_ISSUE_PATTERN.matcher(message);
		
		if (printIssueMatcher.matches()) {
			int issueId = Integer.parseInt(printIssueMatcher.group(REQ_ISSUE_PATTERN_GROUP_ISSUE));
			handlePrintIssue(issueId);			
		}		
	}
	
	private void handlePrintIssue(int issueId) {
		try {
			Issue issue = new IssueService().getIssue("syncany", "syncany", issueId);
			
			sendAndLog("Issue #" + issueId + " (" + issue.getState() + "): "+ issue.getTitle());
			sendAndLog(issue.getHtmlUrl());
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}	

	private void sendAndLog(String message) {
		sendMessage(channel, message);
		appendToLog(String.format("<%s> %s", shortenStr(getNick(), 50), shortenStr(message, 1024)));
	}

	@Override
	protected void onJoin(String channel, String sender, String login, String hostname) {
		appendToLog(String.format("%s (%s@%s) joined %s.", 
			shortenStr(sender, 50), shortenStr(login, 25), shortenStr(hostname, 150), shortenStr(channel, 20)));		
	}

	@Override
	protected void onPart(String channel, String sender, String login, String hostname) {
		appendToLog(String.format("%s (%s@%s) left %s.", 
			shortenStr(sender, 50), shortenStr(login, 25), shortenStr(hostname, 150), shortenStr(channel, 20)));		
	}
	
	@Override
	protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {		
		appendToLog(String.format("%s (%s@%s) left irc: %s.", 
			shortenStr(sourceNick, 50), shortenStr(sourceLogin, 25), shortenStr(sourceHostname, 150), shortenStr(reason, 200)));		
	}
		
	private String shortenStr(String inputStr, int maxLength) {
		if (inputStr.length() > maxLength) {
		    inputStr = inputStr.substring(0, maxLength);
		}
		
		return inputStr;
	}
	
	private void appendToLog(String line) {		
		String currentLogFileName = String.format("%s.log.%s", channel, new SimpleDateFormat("ddMMMYYY").format(new Date()));
		String currentTimeStamp = new SimpleDateFormat("HH:mm").format(new Date());
		String logLine = String.format("[%s] %s\n", currentTimeStamp, line);
		
		try (BufferedWriter logFile = new BufferedWriter(new FileWriter(new File(logdir, currentLogFileName), true))) {
			logFile.append(logLine);
			logFile.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
