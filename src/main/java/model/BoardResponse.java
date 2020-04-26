package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BoardResponse {
	@JsonProperty("mid_price")
	private double midPrice;
	private PriceSize[] bids;
	private PriceSize[] asks;

	public double getMidPrice() {
		return midPrice;
	}

	public void setMidPrice(double midPrice) {
		this.midPrice = midPrice;
	}

	public PriceSize[] getBids() {
		return bids;
	}

	public void setBids(PriceSize[] bids) {
		this.bids = bids;
	}

	public PriceSize[] getAsks() {
		return asks;
	}

	public void setAsks(PriceSize[] asks) {
		this.asks = asks;
	}
}
