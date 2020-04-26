package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChildOrderResponse {
	@JsonProperty("child_order_acceptance_id")
	private String childOrderAcceptanceId;

	public String getChildOrderAcceptanceId() {
		return childOrderAcceptanceId;
	}

	public void setChildOrderAcceptanceId(String childOrderAcceptanceId) {
		this.childOrderAcceptanceId = childOrderAcceptanceId;
	}
}
