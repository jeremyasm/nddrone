/* 
 * Wrote by Yaoxing Li
 * FOR INTERNAL USE ONLY 
 */
package routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

/**
 * Implementation of NDDR 
 * @author liyaoxing
 *
 */
public class NDDRRouter extends ActiveRouter {
	
	//----------------- Configuration ------------------
	public static final int Nr_OF_DATA_NAMES = 10; // Configurable, [1,26], must be the same with the number of "toHosts" in settings.txt
	public static final int Nr_OF_HOSTS = 100; // Configurable, must be the same with "Group.nrofHosts" in settings.txt
	
	public static final boolean DISTANCE_ENABLED = true;

	public static final int DEFAULT_DEST_DIST = 999;
	public static final boolean DEFAULT_E_MARK = false;
	//--------------------------------------------------

	public static final String NDDR_NS = "NDDRRouter";

	public static Random rd = new Random();

	protected static String[] dataNameList = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N",
			"O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z" };
	
	public static int dataNameIndex = 0;
	public static int hostIndex = 0;

	// NDDR-specific fields
	public static final String TYPE = "type";
	public static final String DATA_NAME = "dataName";
	public static final String SRC_DIST = "srcDist";
	public static final String DEST_DIST = "destDist";
	public static final String SEQ_NUM = "seqnum";
	public static final String CONTENT = "dataContent";
	public static final String E_MARK = "isEligible"; 
	public static final String DATA_CONTENT_FOR_TEST = "data content for test";
	
	public static final String TYPE_INTEREST = "Interest";
	public static final String TYPE_DATA = "Data";
	
	public int seqnum = 0; // sequence number to mark the sequence of messages created on this router

	/** Distance Table, an entry is like: < host, distance - seqnum > */
	private Map<DTNHost, Tuple<Integer, Integer>> distance_table; 
	
	/** Content Storage, an entry is like: < dataName, dataContent >  */
	private Map<String, String> content_storage;  
	
	/** Pending Interest Table, an entry is like: < dataName, requester collection >  */
	private Map<String, ArrayList<DTNHost>> pending_interest_table; 


	public NDDRRouter(Settings s) {
		super(s);
		initDataStructures();
	}

	protected NDDRRouter(NDDRRouter r) {
		super(r);
		initDataStructures();
	}
	
	/**
	 * Initiate data structure in each NDDR router
	 */
	private void initDataStructures() {
				
		// create the three components in NDDR router
		this.distance_table = new HashMap<DTNHost, Tuple<Integer, Integer>>();
		this.content_storage = new HashMap<String, String>();
		this.pending_interest_table = new HashMap<String, ArrayList<DTNHost>>();

		// prepared some data in some nodes which are different from the requester nodes
		if (( hostIndex >= (Nr_OF_HOSTS - Nr_OF_DATA_NAMES) && hostIndex < Nr_OF_HOSTS ) 
				&& (dataNameIndex < Nr_OF_DATA_NAMES)
				&& (rd.nextInt(Nr_OF_DATA_NAMES) < Nr_OF_DATA_NAMES) ) {
			
			String pickedDataName = dataNameList[dataNameIndex];
			this.content_storage.put(pickedDataName, DATA_CONTENT_FOR_TEST);
			dataNameIndex++;
		}
		
		hostIndex ++;

	}
	
	/**
	 * Update distance information 
	 * @param m, a message
	 * @param m_seqnum, seqnum of the message
	 * @param m_srcID, srcID of the message
	 * @param m_srcDist, srcDist of the message
	 */
	private void updateDistanceInformation(Message m, int m_seqnum, DTNHost m_srcID, int m_srcDist) {

		if (this.distance_table.containsKey(m_srcID)) { // Distance Table contains a m_srcID-related entry
			if (m_seqnum > (int) this.distance_table.get(m_srcID).getValue()) { // the distance information carried by this message is fresher 
				this.distance_table.put(m_srcID, new Tuple<Integer, Integer>(m_srcDist, m_seqnum));
			} else if (m_seqnum == (int) this.distance_table.get(m_srcID).getValue()) {
				if (m_srcDist < (int) this.distance_table.get(m_srcID).getKey())
					this.distance_table.put(m_srcID, new Tuple<Integer, Integer>(m_srcDist, m_seqnum));
				else if (m_srcDist > (int) this.distance_table.get(m_srcID).getKey()) {
					m.updateProperty(SRC_DIST, this.distance_table.get(m_srcID).getKey());
				}
			} else { // m_seqnum < (int) this.distance_table.get(m_srcID).getValue()  // the distance information in Distance Table is fresher 
				m.updateProperty(SRC_DIST, this.distance_table.get(m_srcID).getKey());
				m.updateProperty(SEQ_NUM, this.distance_table.get(m_srcID).getValue());
			}
		} else{ // Distance Table doesn't contain a m_srcID-related entry 
			this.distance_table.put(m_srcID, new Tuple<Integer, Integer>(m_srcDist, m_seqnum)); 
		}

	}

	/**
	 * Process a received message inside the NDDR router 
	 * @param m , the message received
	 */
	private void processReceivedMessage(Message m){
				
		//--------------- For both INTEREST & DATA ----------------
		// read common fields of the message, both INTEREST and DATA have these fields
		DTNHost m_srcID = m.getFrom();
		int m_srcDist = (int) m.getProperty(SRC_DIST);
		int m_seqnum = (int) m.getProperty(SEQ_NUM);
		String m_dataName = m.getDataName();
		String m_type = (String) m.getProperty(TYPE);
		int m_size = m.getSize();
		
		// one more hop from the src
		m_srcDist++;
		m.updateProperty(SRC_DIST, m_srcDist);

		updateDistanceInformation(m, m_seqnum, m_srcID, m_srcDist); // message's distance-related fields interacts with Distance Table

		// --------------- For DATA ONLY ----------------
		if (m_type.equals(TYPE_DATA)) {
			// read Data-specific fields, these fields are null in an INTEREST.
			String m_data = (String) m.getProperty(CONTENT);
			DTNHost m_destID = m.getTo();
			int m_destDist = (int) m.getProperty(DEST_DIST);

			boolean isEligible = true;
			
			// process in DT
			if (this.distance_table.containsKey(m_destID)) {
				if ((int) this.distance_table.get(m_destID).getKey() < m_destDist ) {
					isEligible = true;
					m.updateProperty(DEST_DIST, this.distance_table.get(m_destID).getKey()); //XXX the only step to update a message's destDist		
				} else 
					isEligible = false;
			} else{ 
				isEligible = false;
			}

			// process in CS for Data
			if (!this.content_storage.containsKey(m_dataName)) {
				this.content_storage.put(m_dataName, m_data);
			} 
			
			// process DATA in Pending Interest Table
			if (this.pending_interest_table.containsKey(m_dataName)) { // hit the dataName !
				ArrayList<DTNHost> requesters = this.pending_interest_table.get(m_dataName);
				if(isEligible) { // already has a DATA for m_destID
					requesters.remove(m_destID); // no need to create a new DATA message for m_destID
					for(DTNHost toHost:requesters){
						int destDist = this.distance_table.containsKey(toHost)? this.distance_table.get(toHost).getKey():DEFAULT_DEST_DIST;
						createNewDataMessage(m_dataName, content_storage.get(m_dataName), toHost, destDist, m_size); 
					}
					this.pending_interest_table.remove(m_dataName);
				}else{
					boolean is_m_destIDARequester = requesters.remove(m_destID);
					for(DTNHost toHost:requesters){
						int destDist = this.distance_table.containsKey(toHost)? this.distance_table.get(toHost).getKey():DEFAULT_DEST_DIST;
						createNewDataMessage(m_dataName, content_storage.get(m_dataName), toHost, destDist, m_size); 
					}
					if(is_m_destIDARequester){ // m_destID has already exists in the requesters
						requesters.clear();
						requesters.add(m_destID);
					}else{
						this.pending_interest_table.remove(m_dataName);
					}
					removeFinishedMessage(m.getId()); // the DATA message finished its tasks, remove it 
				}
			}else{ // could not hit the m_dataName in PIT 
				if(!isEligible){
					removeFinishedMessage(m.getId()); // the DATA message finished its tasks, remove it 
				}
			}
		} // -------------- end if(DATA) ---------------

		// --------------- For INTEREST ONLY ----------------
		if (m_type.equals(TYPE_INTEREST)) {

			boolean hitInCS = false;
			
			// process INTEREST in Content Storage
			if (this.content_storage.containsKey(m_dataName)) { // hit the dataName !
				hitInCS = true;
				int destDist = this.distance_table.containsKey(m_srcID)? this.distance_table.get(m_srcID).getKey():DEFAULT_DEST_DIST;
				createNewDataMessage(m, destDist, content_storage.get(m_dataName));  // create a DATA  
				deleteMessage(m.getId(), false);  // remove the INTEREST
			}

			// process INTEREST in Pending Interest Table
			if (this.pending_interest_table.containsKey(m_dataName)) { // hit the dataName !
				ArrayList<DTNHost> requesters = this.pending_interest_table.get(m_dataName);
				if(hitInCS){
					requesters.remove(m_srcID); 
					for(DTNHost toHost:requesters){
						int destDist = this.distance_table.containsKey(toHost)? this.distance_table.get(toHost).getKey():DEFAULT_DEST_DIST;
						createNewDataMessage(m_dataName, content_storage.get(m_dataName), toHost, destDist, m_size); 
					}
					this.pending_interest_table.remove(m_dataName);
				} else if (!requesters.contains(m_srcID))
					requesters.add(m_srcID); 
			} else if(!hitInCS){
				ArrayList<DTNHost> tmp_reqList = new ArrayList<DTNHost>();
				tmp_reqList.add(m_srcID);
				this.pending_interest_table.put(m_dataName, tmp_reqList);
			}

		} // -------------- end if(INTEREST) --------------

	}
	
	/**
	 * Create a new INTEREST message
	 * @param msg
	 */
	@Override
	public boolean createNewMessage(Message msg) {
		
		msg.setTo(null); //XXX an interest message has no destination
		msg.setTtl(this.msgTtl);
		msg.setDataName(drawDataName());
		msg.addProperty(TYPE, TYPE_INTEREST);
		msg.addProperty(SRC_DIST, new Integer(0));
		msg.addProperty(DEST_DIST, null);
		msg.addProperty(SEQ_NUM, this.seqnum);
		msg.addProperty(CONTENT, null);

		makeRoomForNewMessage(msg.getSize());
		addToMessages(msg, true);
		this.seqnum++; // everytime the router create a new msg, seqnum++

		return true;
	}

	/**
	 * Create a new DATA message
	 * @param interestMsg
	 * @param destDist
	 * @param dataContent
	 */
	public void createNewDataMessage(Message interestMsg, int destDist, String dataContent) {
		
		DTNHost newTo = interestMsg.getFrom(); 
		DTNHost newFrom = this.getHost(); 
		String id = "D_" + this.getHost() + "_" + this.seqnum; 
		int size = interestMsg.getSize();
		String dataName = interestMsg.getDataName();
		
		Message dataMsg = new Message(newFrom, newTo, id, size);
		dataMsg.setTtl(this.msgTtl);
		dataMsg.setDataName(dataName);
		dataMsg.addProperty(TYPE, TYPE_DATA); 
		dataMsg.addProperty(SRC_DIST, new Integer(0));
		dataMsg.addProperty(DEST_DIST, destDist);
		dataMsg.addProperty(SEQ_NUM, this.seqnum);
		dataMsg.addProperty(CONTENT, dataContent);
		
		makeRoomForNewMessage(dataMsg.getSize());
		addToMessages(dataMsg, true); 
		this.seqnum++; // everytime the router create a new msg, seqnum++
	}
	
	/**
	 * Create a new DATA message
	 * @param dataName
	 * @param dataContent
	 * @param dest
	 * @param destDist
	 * @param size
	 */
	public void createNewDataMessage(String dataName, String dataContent, DTNHost dest, int destDist, int size) {

		String id = "D_" + this.getHost() + "_" + this.seqnum;
		
		Message dataMsg = new Message(this.getHost(), dest, id, size);
		dataMsg.setTtl(this.msgTtl);
		dataMsg.setDataName(dataName);
		dataMsg.addProperty(TYPE, TYPE_DATA); 
		dataMsg.addProperty(SRC_DIST, new Integer(0));
		dataMsg.addProperty(DEST_DIST, destDist);
		dataMsg.addProperty(SEQ_NUM, this.seqnum);
		dataMsg.addProperty(CONTENT, dataContent);
		
		makeRoomForNewMessage(dataMsg.getSize());
		addToMessages(dataMsg, true); 
		this.seqnum++; // everytime the router create a new msg, seqnum++

	}

	/**
	 * Draws a random data name from the data name list
	 * 
	 * @return A random data name
	 */
	protected static String drawDataName() {

		Random r = new Random();
		return dataNameList[r.nextInt(Nr_OF_DATA_NAMES)];

	}

	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring
		}

		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}

		this.tryAllMessagesToAllConnections(); 

	}
	
	@Override
	public NDDRRouter replicate() {
		return new NDDRRouter(this);
	}
		
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		
		Message msg = super.messageTransferred(id, from); //the msg recevied ... it might be an Interest or Data
		this.processReceivedMessage(msg); 
		return msg; 
	}
	
	@Override
	public Connection exchangeDeliverableMessages() {
		List<Connection> connections = getConnections();

		if (connections.size() == 0) {
			return null;
		}
		
		@SuppressWarnings(value = "unchecked")
		Tuple<Message, Connection> t =
			tryMessagesForConnected(sortByQueueMode(getMessagesForConnected()));
			//call ---> startTransfer

		if (t != null) {
			return t.getValue(); // started transfer
		}
		
		for (Connection con : connections) {
			if (con.getOtherNode(getHost()).requestDeliverableMessages(con)) {
				return con;
			}
		}
		
		return null;
	}
	
	@Override
	public List<Tuple<Message, Connection>> getMessagesForConnected() {
		if (getNrofMessages() == 0 || getConnections().size() == 0) {
			/* no messages -> empty list */
			return new ArrayList<Tuple<Message, Connection>>(0); 
		}
		List<Tuple<Message, Connection>> forTuples = 
			new ArrayList<Tuple<Message, Connection>>();
		for (Message m : getMessageCollection()) {
			for (Connection con : getConnections()) {
				DTNHost to = con.getOtherNode(getHost());		
				String m_type = (String) m.getProperty(TYPE);
				if(m_type.equals(TYPE_DATA)){ // only Data message has a destination, no need to handle Interest since it's flooded.
					if (m.getTo() == to) {
						forTuples.add(new Tuple<Message, Connection>(m,con));
					}
				}
			}
		}
		return forTuples;
	}
	
	@Override
	public boolean requestDeliverableMessages(Connection con) {
		if (isTransferring()) {
			return false;
		}
		
		DTNHost other = con.getOtherNode(getHost());
		/* do a copy to avoid concurrent modification exceptions 
		 * (startTransfer may remove messages) */
		ArrayList<Message> temp = 
			new ArrayList<Message>(this.getMessageCollection());
		for (Message m : temp) {
			String m_type = (String) m.getProperty(TYPE);
			if(m_type.equals(TYPE_DATA)){ // only Data message has a destination, no need to handle Interest since it's flooded.
				if (other == m.getTo()) {
					if (startTransfer(m, con) == RCV_OK) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Removes the message with the given ID from this router, if the router
	 * has that message; otherwise does nothing. If the router was transferring
	 * the message, the transfer is aborted.
	 * @param id ID of the message to be removed
	 */
	public void removeFinishedMessage(String id) {
		if (this.hasMessage(id)) {
			for (Connection c : this.sendingConnections) {
				/* if sending the message-to-be-removed, cancel transfer */
				if (c.getMessage().getId().equals(id)) {
					c.abortTransfer();
				}
			}
			this.deleteMessage(id, false);			
		}
	}
	
}