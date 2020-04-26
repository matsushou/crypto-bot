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
	 * @return —a‚¯“ü‚ê‚½Ø‹’‹à‚Ì•]‰¿Šz(‰~)
	 */
	public int getCollateral() {
		return collateral;
	}

	public void setCollateral(int collateral) {
		this.collateral = collateral;
	}

	/**
	 * @return Œš‹Ê‚Ì•]‰¿‘¹‰v(‰~)
	 */
	public String getOpenPositionPnl() {
		return openPositionPnl;
	}

	public void setOpenPositionPnl(String openPositionPnl) {
		this.openPositionPnl = openPositionPnl;
	}

	/**
	 * @return Œ»İ‚Ì•K—vØ‹’‹à(‰~)
	 */
	public String getRequireCollateral() {
		return requireCollateral;
	}

	public void setRequireCollateral(String requireCollateral) {
		this.requireCollateral = requireCollateral;
	}

	/**
	 * @return Œ»İ‚ÌØ‹’‹àˆÛ—¦
	 */
	public String getKeepRate() {
		return keepRate;
	}

	public void setKeepRate(String keepRate) {
		this.keepRate = keepRate;
	}

}
