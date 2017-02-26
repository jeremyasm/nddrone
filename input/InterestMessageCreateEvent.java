/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import core.DTNHost;
import core.Message;
import core.World;

/**
 * External event for creating a message.
 */
@SuppressWarnings("serial")
public class InterestMessageCreateEvent extends MessageEvent {
	private int size;
	private int responseSize;
	

	private String dataName;
	
	/**
	 * Creates a message creation event with a optional response request
	 * @param from The creator of the message
	 * @param to Where the message is destined to
	 * @param id ID of the message
	 * @param size Size of the message
	 * @param responseSize Size of the requested response message or 0 if
	 * no response is requested
	 * @param time Time, when the message is created
	 * @param dataName dataName
	 */

	//lyx
	public InterestMessageCreateEvent(int from, int to, String id, int msgSize,
			int responseSize2, double nextEventsTime, String dataName) {
		
		super(from,to, id, nextEventsTime);
		this.size = msgSize;
		this.responseSize = responseSize2;
		this.dataName = dataName;
		
	}


	/**
	 * Creates the message this event represents. 
	 */
	@Override
	public void processEvent(World world) {
		DTNHost to = world.getNodeByAddress(this.toAddr);
		DTNHost from = world.getNodeByAddress(this.fromAddr);			
		//lyx
		Message m = new Message(from, to, this.id, this.size, this.dataName);
		m.setResponseSize(this.responseSize);
		from.createNewMessage(m);
	}
	
	@Override
	public String toString() {
		return super.toString() + " [" + fromAddr + "->" + toAddr + "] " +
		"size:" + size + 
		"dataName: " + dataName + " CREATE";
	}
}