package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CollateralResponse {

	private int collateral;

	@JsonProperty("open_position_pnl")
	private String openPositionPnl;

	@JsonProperty("require_collateral")
	private String requireCollateral;

	@JsonProperty("keep_rate")
	private String keepRate;

	/**
	 * @return 預け入れた証拠金の評価額(円)
	 */
	public int getCollateral() {
		return collateral;
	}

	public void setCollateral(int collateral) {
		this.collateral = collateral;
	}

	/**
	 * @return 建玉の評価損益(円)
	 */
	public String getOpenPositionPnl() {
		return openPositionPnl;
	}

	public void setOpenPositionPnl(String openPositionPnl) {
		this.openPositionPnl = openPositionPnl;
	}

	/**
	 * @return 現在の必要証拠金(円)
	 */
	public String getRequireCollateral() {
		return requireCollateral;
	}

	public void setRequireCollateral(String requireCollateral) {
		this.requireCollateral = requireCollateral;
	}

	/**
	 * @return 現在の証拠金維持率
	 */
	public String getKeepRate() {
		return keepRate;
	}

	public void setKeepRate(String keepRate) {
		this.keepRate = keepRate;
	}

}
