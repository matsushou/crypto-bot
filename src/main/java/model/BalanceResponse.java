package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BalanceResponse {
	@JsonProperty("currency_code")
	private String currencyCode;

	private double amount;

	private double available;

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public double getAvailable() {
		return available;
	}

	public void setAvailable(double available) {
		this.available = available;
	}

}
