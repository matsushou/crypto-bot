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
	 * @return $BMB$1F~$l$?>Z5r6b$NI>2A3[(B($B1_(B)
	 */
	public int getCollateral() {
		return collateral;
	}

	public void setCollateral(int collateral) {
		this.collateral = collateral;
	}

	/**
	 * @return $B7z6L$NI>2AB;1W(B($B1_(B)
	 */
	public String getOpenPositionPnl() {
		return openPositionPnl;
	}

	public void setOpenPositionPnl(String openPositionPnl) {
		this.openPositionPnl = openPositionPnl;
	}

	/**
	 * @return $B8=:_$NI,MW>Z5r6b(B($B1_(B)
	 */
	public String getRequireCollateral() {
		return requireCollateral;
	}

	public void setRequireCollateral(String requireCollateral) {
		this.requireCollateral = requireCollateral;
	}

	/**
	 * @return $B8=:_$N>Z5r6b0];}N((B
	 */
	public String getKeepRate() {
		return keepRate;
	}

	public void setKeepRate(String keepRate) {
		this.keepRate = keepRate;
	}

}
