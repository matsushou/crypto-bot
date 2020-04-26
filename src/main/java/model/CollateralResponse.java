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
	 * @return �a�����ꂽ�؋����̕]���z(�~)
	 */
	public int getCollateral() {
		return collateral;
	}

	public void setCollateral(int collateral) {
		this.collateral = collateral;
	}

	/**
	 * @return ���ʂ̕]�����v(�~)
	 */
	public String getOpenPositionPnl() {
		return openPositionPnl;
	}

	public void setOpenPositionPnl(String openPositionPnl) {
		this.openPositionPnl = openPositionPnl;
	}

	/**
	 * @return ���݂̕K�v�؋���(�~)
	 */
	public String getRequireCollateral() {
		return requireCollateral;
	}

	public void setRequireCollateral(String requireCollateral) {
		this.requireCollateral = requireCollateral;
	}

	/**
	 * @return ���݂̏؋����ێ���
	 */
	public String getKeepRate() {
		return keepRate;
	}

	public void setKeepRate(String keepRate) {
		this.keepRate = keepRate;
	}

}
