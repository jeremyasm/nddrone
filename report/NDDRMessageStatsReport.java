/* 
 * Wrote by Yaoxing Li
 * FOR INTERNAL USE ONLY 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import routing.NDDRRouter;

/**
 * Report for generating message statistics for NDDR performance test
 * @author liyaoxing
 */
public class NDDRMessageStatsReport extends Report implements MessageListener {
	private Map<String, Double> creationTimes;
	private List<Double> latencies;
	private List<Integer> hopCountsOfData;
	private List<Double> msgBufferTime;
	private List<Double> rtt; // round trip times
	private HashSet<String> interestCreateSet;
	private HashSet<String> interestDeliverySet;
	private HashSet<String> dataCreateSet;
	private HashSet<String> dataDeliverySet;

	private int nrofDropped;
	private int nrofRemoved;
	private int nrofStarted;
	private int nrofAborted;
	private int nrofRelayed;
	private int nrofCreated;
	private int nrofResponseReqCreated;
	private int nrofResponseDelivered;
	private int nrofDelivered;

	private int nrofInterestCreated;
	private int nrofDifferentInterestsCreatedOnDifferentHosts;
	private int nrofDifferentInterestsFromDifferentHostsDelivered;
	private int nrofDifferentDataCreatedToDifferentHosts;
	private int nrofDifferentDataDeliveredToDifferentHosts;

	/**
	 * Constructor.
	 */
	public NDDRMessageStatsReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		this.creationTimes = new HashMap<String, Double>();
		this.latencies = new ArrayList<Double>();
		this.msgBufferTime = new ArrayList<Double>();
		this.hopCountsOfData = new ArrayList<Integer>();
		this.rtt = new ArrayList<Double>();
		this.interestCreateSet = new HashSet<String>();
		this.interestDeliverySet = new HashSet<String>();
		this.dataCreateSet = new HashSet<String>();
		this.dataDeliverySet = new HashSet<String>();

		this.nrofDropped = 0;
		this.nrofRemoved = 0;
		this.nrofStarted = 0;
		this.nrofAborted = 0;
		this.nrofRelayed = 0;
		this.nrofCreated = 0;
		this.nrofInterestCreated = 0;
		this.nrofDifferentInterestsCreatedOnDifferentHosts = 0;
		this.nrofDifferentDataCreatedToDifferentHosts = 0;
		this.nrofResponseReqCreated = 0;
		this.nrofResponseDelivered = 0;
		this.nrofDelivered = 0;
		this.nrofDifferentDataDeliveredToDifferentHosts = 0;
		this.nrofDifferentInterestsFromDifferentHostsDelivered = 0;
	}

	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		if (isWarmupID(m.getId())) {
			return;
		}

		if (dropped) {
			this.nrofDropped++;
		} else {
			this.nrofRemoved++;
			if(m.getProperty(NDDRRouter.TYPE).equals(NDDRRouter.TYPE_INTEREST)){
				this.nrofDelivered++; // delivery of interest
				if (this.interestDeliverySet.add(m.getFrom() + "_" + m.getDataName())) 
					this.nrofDifferentInterestsFromDifferentHostsDelivered++;
			}
		}

		this.msgBufferTime.add(getSimTime() - m.getReceiveTime());
	}

	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofAborted++;
	}

	public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean finalTarget) {

		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofRelayed++;
		if (finalTarget) {
			this.nrofDelivered++; //delivery of data
			if (this.dataDeliverySet.add(m.getTo() + "_" + m.getDataName())) {
				this.nrofDifferentDataDeliveredToDifferentHosts++;
				this.latencies.add(getSimTime() - this.creationTimes.get(m.getId()));
				this.hopCountsOfData.add(m.getHops().size() - 1);
			}
		}
	}

	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getId());
			return;
		}
		this.creationTimes.put(m.getId(), getSimTime());
		this.nrofCreated++;
		if (m.getProperty(NDDRRouter.TYPE).equals(NDDRRouter.TYPE_DATA)) {
			if (this.dataCreateSet.add(m.getTo() + "_" + m.getDataName())) {
				this.nrofDifferentDataCreatedToDifferentHosts++;
			}
		}
		if (m.getProperty(NDDRRouter.TYPE).equals(NDDRRouter.TYPE_INTEREST)) {
			this.nrofInterestCreated++;
			if (this.interestCreateSet.add(m.getFrom() + "_" + m.getDataName())) {
				this.nrofDifferentInterestsCreatedOnDifferentHosts++;
			}
		}
		if (m.getResponseSize() > 0) {
			this.nrofResponseReqCreated++;
		}
	}

	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofStarted++;
	}

	@Override
	public void done() {
		write("Message stats for scenario " + getScenarioName() + "\nsim_time: " + format(getSimTime()));
		double deliveryProb = 0; // delivery probability
		double overHead = Double.NaN; // overhead ratio
		double interestDeliveryProb = 0; // delivery probability of interest messages
		double dataDeliveryProb = 0; // delivery probability of data messages

		if (this.nrofDifferentInterestsCreatedOnDifferentHosts > 0) {
			deliveryProb = (1.0 * this.nrofDifferentDataDeliveredToDifferentHosts) / this.nrofDifferentInterestsCreatedOnDifferentHosts;
		}
		if (this.nrofDifferentInterestsCreatedOnDifferentHosts > 0) {
			interestDeliveryProb = (1.0 * this.nrofDifferentInterestsFromDifferentHostsDelivered) / this.nrofDifferentInterestsCreatedOnDifferentHosts;
		}
		if (this.nrofDifferentDataCreatedToDifferentHosts > 0) {
			dataDeliveryProb = (1.0 * this.nrofDifferentDataDeliveredToDifferentHosts) / this.nrofDifferentDataCreatedToDifferentHosts;
		}
		if (this.nrofDelivered > 0) {
			overHead = (1.0 * (this.nrofRelayed - this.nrofDelivered)) / this.nrofDelivered;
		}

		String statsText = "interet_created: " + this.nrofInterestCreated
				+ "\ninterest_created (de-duplicated): " + this.nrofDifferentInterestsCreatedOnDifferentHosts // for multiple creation of a same interest on a same host, only take one into account 
				+ "\ndata_created (de-duplicated): " + this.nrofDifferentDataCreatedToDifferentHosts // for multiple creation of a same data on a same host, only take one into account
				+ "\nstarted: " + this.nrofStarted 
				+ "\nrelayed: " + this.nrofRelayed 
				+ "\naborted: " + this.nrofAborted 
				+ "\ndropped: " + this.nrofDropped 
				+ "\nremoved: " + this.nrofRemoved
				+ "\ndelivered: " + this.nrofDelivered //TODO
				+ "\ninterest_delivered (de-duplicated): " + this.nrofDifferentInterestsFromDifferentHostsDelivered // for multiple deliveries of a same interest from a same host, only take one into account 
				+ "\ndata_delivered (de-duplicated): " + this.nrofDifferentDataDeliveredToDifferentHosts // for multiple deliveries of a same data to a same host, only take one into account 
				+ "\ndelivery_prob: " + format(deliveryProb)
				+ "\ndelivery_prob_of_interest: " + format(interestDeliveryProb) 
				+ "\ndelivery_prob_of_data: " + format(dataDeliveryProb) 
				+ "\noverhead_ratio: " + format(overHead)
				+ "\nlatency_avg: " + getAverage(this.latencies) 
				+ "\nhopcount_avg_of_data: " + getIntAverage(this.hopCountsOfData);

		write(statsText);
		super.done();
	}

}
