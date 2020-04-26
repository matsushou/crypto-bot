package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PositionResponse {

	@JsonProperty("product_code")
	private String productCode;

	private String side;

	private String price;

	private String size;

	private String commission;

	@JsonProperty("swap_point_accumulate")
	private String swapPointAccumulate;

	@JsonProperty("require_collateral")
	private String requireCollateral;

	@JsonProperty("open_date")
	private String openDate;

	private String leverage;

	private String pnl;

	private String sfd;

	public String getProductCode() {
		return productCode;
	}

	public void setProductCode(String productCode) {
		this.productCode = productCode;
	}

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public String getPrice() {
		return price;
	}

	public void setPrice(String price) {
		this.price = price;
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getCommission() {
		return commission;
	}

	public void setCommission(String commission) {
		this.commission = commission;
	}

	public String getSwapPointAccumulate() {
		return swapPointAccumulate;
	}

	public void setSwapPointAccumulate(String swapPointAccumulate) {
		this.swapPointAccumulate = swapPointAccumulate;
	}

	public String getRequireCollateral() {
		return requireCollateral;
	}

	public void setRequireCollateral(String requireCollateral) {
		this.requireCollateral = requireCollateral;
	}

	public String getOpenDate() {
		return openDate;
	}

	public void setOpenDate(String openDate) {
		this.openDate = openDate;
	}

	public String getLeverage() {
		return leverage;
	}

	public void setLeverage(String leverage) {
		this.leverage = leverage;
	}

	public String getPnl() {
		return pnl;
	}

	public void setPnl(String pnl) {
		this.pnl = pnl;
	}

	public String getSfd() {
		return sfd;
	}

	public void setSfd(String sfd) {
		this.sfd = sfd;
	}
}
