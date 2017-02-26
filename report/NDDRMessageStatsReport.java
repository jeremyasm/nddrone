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
	private List<Integer> hopCounts;
	private List<Double> msgBufferTime;
	private List<Double> rtt; // round trip times
	private HashSet<String> hashSet;

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
	private int nrofDataCreated;
	private int nrofDataDelivered;

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
		this.hopCounts = new ArrayList<Integer>();
		this.rtt = new ArrayList<Double>();
		this.hashSet = new HashSet<String>();

		this.nrofDropped = 0;
		this.nrofRemoved = 0;
		this.nrofStarted = 0;
		this.nrofAborted = 0;
		this.nrofRelayed = 0;
		this.nrofCreated = 0;
		this.nrofInterestCreated = 0;
		this.nrofDataCreated = 0;
		this.nrofResponseReqCreated = 0;
		this.nrofResponseDelivered = 0;
		this.nrofDelivered = 0;
		this.nrofDataDelivered = 0;
	}

	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		if (isWarmupID(m.getId())) {
			return;
		}

		if (dropped) {
			this.nrofDropped++;
		} else {
			this.nrofRemoved++;
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
			this.nrofDelivered++;
			if (this.hashSet.add(m.getTo() + "_" + m.getDataName())) {
				this.latencies.add(getSimTime() - this.creationTimes.get(m.getId()));
				this.nrofDataDelivered++;
				this.hopCounts.add(m.getHops().size() - 1);
				if (m.isResponse()) {
					this.rtt.add(getSimTime() - m.getRequest().getCreationTime());
					this.nrofResponseDelivered++;
				}
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
		if (m.getProperty("type").equals(NDDRRouter.TYPE_DATA)) {
			this.nrofDataCreated++;
		}
		if (m.getProperty("type").equals(NDDRRouter.TYPE_INTEREST)) {
			this.nrofInterestCreated++;
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
		double responseProb = 0; // request-response success probability
		double overHead = Double.NaN; // overhead ratio

		if (this.nrofInterestCreated > 0) {
			deliveryProb = (1.0 * this.nrofDataDelivered) / this.nrofInterestCreated;
		}
		if (this.nrofDelivered > 0) {
			overHead = (1.0 * (this.nrofRelayed - this.nrofDelivered)) / this.nrofDelivered;
		}
		if (this.nrofResponseReqCreated > 0) {
			responseProb = (1.0 * this.nrofResponseDelivered) / this.nrofResponseReqCreated;
		}

		String statsText = "created: " + this.nrofCreated 
				+ "\nInteret created: " + this.nrofInterestCreated 
				+ "\nData created: " + this.nrofDataCreated
				+ "\nstarted: " + this.nrofStarted 
				+ "\nrelayed: " + this.nrofRelayed 
				+ "\naborted: " + this.nrofAborted 
				+ "\ndropped: " + this.nrofDropped 
				+ "\nremoved: " + this.nrofRemoved 
				+ "\nData Delivered: " + this.nrofDataDelivered // for multiple deliveries of a same data, only take one into account 
				+ "\ndelivery_prob: " + format(deliveryProb) 
				+ "\nresponse_prob: " + format(responseProb) 
				+ "\noverhead_ratio: " + format(overHead)
				+ "\nlatency_avg: " + getAverage(this.latencies) 
				+ "\nlatency_med: " + getMedian(this.latencies)
				+ "\nhopcount_avg: " + getIntAverage(this.hopCounts) 
				+ "\nhopcount_med: " + getIntMedian(this.hopCounts)
				+ "\nbuffertime_avg: " + getAverage(this.msgBufferTime) 
				+ "\nbuffertime_med: " + getMedian(this.msgBufferTime); 

		write(statsText);
		super.done();
	}

}