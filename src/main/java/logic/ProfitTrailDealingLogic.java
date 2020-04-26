package logic;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import core.OHLCVUtil;
import exchange.BitFlyerAPIWrapper;
import model.BoardResponse;
import model.BuySellEnum;
import model.ChildOrderResponse;
import model.PositionResponse;
import notification.SlackNotifier;

public class ProfitTrailDealingLogic {

	private final BitFlyerAPIWrapper WRAPPER;
	private final SlackNotifier NOTIFIER;
	private final double TRAIL_PERCENTAGE;
	private final double LOSS_CUT_PERCENTAGE;
	private final double SPREAD;
	private final int INTERVAL;
	private int trailLine = -1;
	private int lossCutLine = -1;
	private boolean trailing = false;
	private int entry = -1;
	private BuySellEnum side;
	private double size;
	private int collateral = -1;
	private int mid = -1;

	private static Logger LOGGER = LogManager.getLogger(ProfitTrailDealingLogic.class);

	public ProfitTrailDealingLogic(BitFlyerAPIWrapper wrapper, SlackNotifier notifier, Map<String, Double> paramMap,
			Map<String, Object> settings) {
		this.WRAPPER = wrapper;
		this.NOTIFIER = notifier;
		this.TRAIL_PERCENTAGE = paramMap.get("trailPercentage");
		this.LOSS_CUT_PERCENTAGE = paramMap.get("lossCutPercentage");
		@SuppressWarnings("unchecked")
		Map<String, Double> logicParam = (Map<String, Double>) settings.get("logic");
		// �p�����[�^�o��
		StringBuilder sb = new StringBuilder();
		sb.append("LogicParams");
		logicParam.forEach((k, v) -> sb.append(" " + k + ":" + v));
		LOGGER.info(sb.toString());
		this.SPREAD = logicParam.get("spread");
		this.INTERVAL = logicParam.get("notifyInterval").intValue();
	}

	public void execute() {
		// ������
		init();
		// �����쐬�X���b�h�̊J�n(1�����̏��������s���f�����̒��ōs��)
		startOhlcvThread();
		// ����ʒm�X���b�h�̊J�n(����I��Slack�ʒm)
		startPeriodicalNotifyThread(INTERVAL);
	}

	private void init() {
		// �|�W�V�����N���A
		positionClear();
		// �������f�܂ŏ����҂�
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// �؋����]���z�擾
		int collateral = getCollateral();
		LOGGER.info("�؋����]���z:" + collateral);
		// �ŏ��͂Ƃ肠��������
		buy();
	}

	private void startOhlcvThread() {
		// ���bMid���擾���A�������쐬����X���b�h
		Thread t = new Thread(() -> {
			int lastSecond = -1;
			int count = 0;
			int[] ohlcv = new int[4];
			// �I�[�o�[�w�b�h���炷���߁A�ŏ���񃊃N�G�X�g���Ă���
			getMidPrice();
			while (true) {
				LocalDateTime now = LocalDateTime.now();
				int second = now.getSecond();
				if (second != lastSecond) {
					// �b���ς������Mid�擾
					int mid = getMidPrice();
					LOGGER.debug("Mid�擾���ʁF" + mid + " ����:" + now);
					this.mid = mid;
					// High,Low�̍X�V�`�F�b�N
					ohlcv = OHLCVUtil.replaceHighAndLow(ohlcv, mid);
					if (count % 60 == 0) {
						// 1�����̏���
						if (count != 0) {
							// ����͏���
							// �O�̕�����close��ݒ�
							ohlcv = OHLCVUtil.setClose(ohlcv, mid);
							// ���s���f
							judge();
							LOGGER.debug(OHLCVUtil.toString(ohlcv));
						}
						// OHLCV�̏�������open,high,low�̐ݒ�
						ohlcv = new int[4];
						ohlcv = OHLCVUtil.setOpen(ohlcv, mid);
						ohlcv = OHLCVUtil.setHigh(ohlcv, mid);
						ohlcv = OHLCVUtil.setLow(ohlcv, mid);
					}
					lastSecond = second;
					count++;
				}
				try {
					// ���񂾂�Y���Ă����Ȃ��悤��50ms�P�ʂƂ���
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, "ohlcvThread");
		t.start();
	}

	private void startPeriodicalNotifyThread(int intervalMin) {
		Thread t = new Thread(() -> {
			while (true) {
				outputCurrentStatusSlack();
				try {
					Thread.sleep(intervalMin * 60 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, "notifyThread");
		t.start();
	}

	private int getMidPrice() {
		BoardResponse board = WRAPPER.getBoard();
		if (board == null) {
			throw new IllegalStateException("Board Response is null.");
		}
		return (int) board.getMidPrice();
	}

	private void judge() {
		LOGGER.debug("judge!");
		outputCurrentStatus();
		int mid = getMidPrice();
		if (side == BuySellEnum.BUY) {
			// �����O�|�W�V����
			// Mid�ɃX�v���b�h�Б��������Z
			int bid = (int) (mid - mid * SPREAD / 200);
			if (bid < lossCutLine || (trailing && bid <= trailLine)) {
				// ���X�J�b�g���C������������A�܂��̓g���[�����Ƀg���[�����C�����������
				// �|�W�V�����N���[�Y���ăh�e��������
				if (bid < lossCutLine) {
					LOGGER.info("�����O�|�W�V�����F���X�J�b�g���܂��BBid:" + bid);
					NOTIFIER.sendMessage("�����O�|�W�V�����F���X�J�b�g���܂��BBid:" + bid);
				} else {
					LOGGER.info("�����O�|�W�V�����F���m���܂��BBid:" + bid);
					NOTIFIER.sendMessage("�����O�|�W�V�����F���m���܂��BBid:" + bid);
				}
				// �h�e��������
				sell();
			} else if (bid > trailLine) {
				if (!trailing) {
					// �g���[���J�n
					LOGGER.info("�����O�|�W�V�����F�g���[���J�n���܂��BBid:" + bid);
					NOTIFIER.sendMessage("�����O�|�W�V�����F�g���[���J�n���܂��BBid:" + bid);
					trailing = true;
				} else {
					// �g���[���X�V
					LOGGER.debug("�����O�|�W�V�����F�g���[���X�V���܂��BBid:" + bid);
				}
				trailLine = bid;
			}
		} else {
			// �V���[�g�|�W�V����
			// Mid�ɃX�v���b�h�Б��������Z
			int ask = (int) (mid + mid * SPREAD / 200);
			if (ask > lossCutLine || (trailing && ask >= trailLine)) {
				// ���X�J�b�g���C�����������A�܂��̓g���[�����Ƀg���[�����C����������
				// �|�W�V�����N���[�Y���ăh�e��������
				if (ask > lossCutLine) {
					LOGGER.info("�V���[�g�|�W�V�����F���X�J�b�g���܂��BAsk:" + ask);
					NOTIFIER.sendMessage("�V���[�g�|�W�V�����F���X�J�b�g���܂��BAsk:" + ask);
				} else {
					LOGGER.info("�V���[�g�|�W�V�����F���m���܂��BAsk:" + ask);
					NOTIFIER.sendMessage("�V���[�g�|�W�V�����F���m���܂��BAsk:" + ask);
				}
				// �h�e��������
				buy();
			} else if (ask < trailLine) {
				if (!trailing) {
					// �g���[���J�n
					LOGGER.info("�V���[�g�|�W�V�����F�g���[���J�n���܂��BAsk:" + ask);
					NOTIFIER.sendMessage("�V���[�g�|�W�V�����F�g���[���J�n���܂��BAsk:" + ask);
					trailing = true;
				} else {
					// �g���[���X�V
					LOGGER.debug("�V���[�g�|�W�V�����F�g���[���X�V���܂��BAsk:" + ask);
				}
				trailLine = ask;
			}
		}
	}

	private void buy() {
		LOGGER.debug("buy!");
		assert (this.side == null || this.side == BuySellEnum.SELL);
		// ���ʌv�Z
		// �h�e�����̃V���[�g�|�W�V�������擾
		double positionSize = getPositionTotalSize(BuySellEnum.SELL);
		int mid = getMidPrice();
		this.mid = getMidPrice();
		// Mid�ɃX�v���b�h�Б��������Z
		int ask = (int) (mid + mid * SPREAD / 200);
		int collateral = getCollateral();

		// �덷�r�����邽��1000�{�ɂ���
		int qtyX1000 = collateral * 1000 / ask;
		if (qtyX1000 < 1) {
			// 0.001�ȉ��̏ꍇ�͔������Ȃ�
			LOGGER.info("�������ʂ�0.001�ȉ��ł��B");
		} else {
			double qty = qtyX1000 / 1000.000;
			// �h�e���������Z
			double qtyWithDoten = qty + positionSize;
			String qtyStr = String.format("%.3f", qtyWithDoten);
			// �L�߂ɉ��i������(Mid��1%�悹��)
			int orderPrice = (int) (mid + mid * 0.01);
			// �������i���������Ƃ݂Ȃ��j
			order(BuySellEnum.BUY, orderPrice, Double.valueOf(qtyStr));
			resetPositionFields(ask, qty, BuySellEnum.BUY);
		}
	}

	private void sell() {
		LOGGER.debug("sell!");
		assert (this.side == null || this.side == BuySellEnum.BUY);
		// ���ʌv�Z
		// �h�e�����̃V���[�g�|�W�V�������擾
		double positionSize = getPositionTotalSize(BuySellEnum.BUY);
		int mid = getMidPrice();
		this.mid = getMidPrice();
		// Mid�ɃX�v���b�h�Б��������Z
		int bid = (int) (mid - mid * SPREAD / 200);
		int collateral = getCollateral();

		// �덷�r�����邽��1000�{�ɂ���
		int qtyX1000 = collateral * 1000 / bid;
		if (qtyX1000 < 1) {
			// 0.001�ȉ��̏ꍇ�͔������Ȃ�
			LOGGER.info("�������ʂ�0.001�ȉ��ł��B");
		} else {
			double qty = qtyX1000 / 1000.000;
			// �h�e���������Z
			double qtyWithDoten = qty + positionSize;
			String qtyStr = String.format("%.3f", qtyWithDoten);
			// �L�߂ɉ��i������(Mid����1%����)
			int orderPrice = (int) (mid - mid * 0.01);
			// �������i���������Ƃ݂Ȃ��j
			order(BuySellEnum.SELL, orderPrice, Double.valueOf(qtyStr));
			resetPositionFields(bid, qty, BuySellEnum.SELL);
		}
	}

	private void positionClear() {
		double longPositionSize = getPositionTotalSize(BuySellEnum.BUY);
		if (longPositionSize != 0) {
			LOGGER.info("�����O�|�W�V�������X�N�G�A�ɂ��܂��B���ʁF" + longPositionSize);
			NOTIFIER.sendMessage("�����O�|�W�V�������X�N�G�A�ɂ��܂��B���ʁF" + longPositionSize);
			int mid = getMidPrice();
			// �L�߂ɉ��i������(Mid����1%����)
			int orderPrice = (int) (mid - mid * 0.01);
			// �������i���������Ƃ݂Ȃ��j
			order(BuySellEnum.SELL, orderPrice, longPositionSize);
		} else {
			double shortPositionSize = getPositionTotalSize(BuySellEnum.SELL);
			if (shortPositionSize != 0) {
				LOGGER.info("�V���[�g�|�W�V�������X�N�G�A�ɂ��܂��B���ʁF" + shortPositionSize);
				NOTIFIER.sendMessage("�V���[�g�|�W�V�������X�N�G�A�ɂ��܂��B���ʁF" + shortPositionSize);
				int mid = getMidPrice();
				// �L�߂ɉ��i������(Mid��1%�悹��)
				int orderPrice = (int) (mid + mid * 0.01);
				// �������i���������Ƃ݂Ȃ��j
				order(BuySellEnum.BUY, orderPrice, shortPositionSize);
			}
		}
	}

	private ChildOrderResponse order(BuySellEnum side, int price, double size) {
		LOGGER.info("[order] side:" + side + " price:" + price + " size:" + size);
		NOTIFIER.sendMessage("[order] side:" + side + " price:" + price + " size:" + size);
		ChildOrderResponse response = WRAPPER.sendChildOrder(side, price, size);
		return response;
	}

	private void resetPositionFields(int entry, double size, BuySellEnum side) {
		if (side == BuySellEnum.BUY) {
			// ���̏ꍇ�A�g���[�����C���������A���X�J�b�g���C��������
			this.trailLine = entry + (int) (entry * TRAIL_PERCENTAGE / 100);
			this.lossCutLine = entry - (int) (entry * LOSS_CUT_PERCENTAGE / 100);
		} else {
			// ���̏ꍇ�A�g���[�����C���������A���X�J�b�g���C��������
			this.trailLine = entry - (int) (entry * TRAIL_PERCENTAGE / 100);
			this.lossCutLine = entry + (int) (entry * LOSS_CUT_PERCENTAGE / 100);
		}
		this.trailing = false;
		this.entry = entry;
		this.side = side;
		this.size = size;
		this.collateral = getCollateral();
		outputCurrentStatus();
		outputCurrentStatusSlack();
	}

	private void outputCurrentStatus() {
		LOGGER.info("[current status] collateral:" + this.collateral + " entry:" + this.entry + " trailLine:"
				+ this.trailLine + " lossCutLine:" + this.lossCutLine + " side:" + this.side + " size:" + this.size
				+ " trailing:" + this.trailing + " mid:" + this.mid);
	}

	private void outputCurrentStatusSlack() {
		NOTIFIER.sendMessage("[current status] collateral:" + this.collateral + " entry:" + this.entry + " trailLine:"
				+ this.trailLine + " lossCutLine:" + this.lossCutLine + " side:" + this.side + " size:" + this.size
				+ " trailing:" + this.trailing + " mid:" + this.mid);
	}

	private double getPositionTotalSize(BuySellEnum side) {
		PositionResponse[] responses = WRAPPER.getPositions();
		double size = 0.000;
		String sideStr = side == BuySellEnum.BUY ? "BUY" : "SELL";
		for (PositionResponse response : responses) {
			if (response.getProductCode().equals("FX_BTC_JPY")) {
				if (response.getSide().equals(sideStr)) {
					size += Double.valueOf(response.getSize());
				}
			}
		}
		return size;
	}

	private int getCollateral() {
		this.collateral = WRAPPER.getCollateral().getCollateral();
		return this.collateral;
	}

}
