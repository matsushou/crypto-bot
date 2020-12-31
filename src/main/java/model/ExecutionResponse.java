package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecutionResponse {

	private String id;

	private String side;

	private int price;

	private String size;

	@JsonProperty("exec_date")
	private String execDate;

	@JsonProperty("buy_child_order_acceptance_id")
	private String buyChildOrderAcceptanceId;

	@JsonProperty("sell_child_order_acceptance_id")
	private String sellChildOrderAcceptanceId;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public int getPrice() {
		return price;
	}

	public void setPrice(int price) {
		this.price = price;
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getExecDate() {
		return execDate;
	}

	public void setExecDate(String execDate) {
		this.execDate = execDate;
	}

	public String getBuyChildOrderAcceptanceId() {
		return buyChildOrderAcceptanceId;
	}

	public void setBuyChildOrderAcceptanceId(String buyChildOrderAcceptanceId) {
		this.buyChildOrderAcceptanceId = buyChildOrderAcceptanceId;
	}

	public String getSellChildOrderAcceptanceId() {
		return sellChildOrderAcceptanceId;
	}

	public void setSellChildOrderAcceptanceId(String sellChildOrderAcceptanceId) {
		this.sellChildOrderAcceptanceId = sellChildOrderAcceptanceId;
	}

}
