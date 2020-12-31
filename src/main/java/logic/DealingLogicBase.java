package logic;

import java.util.Map;

import exchange.BitFlyerAPIWrapper;
import notification.SlackNotifier;

public class DealingLogicBase {

	protected final BitFlyerAPIWrapper WRAPPER;
	protected final SlackNotifier NOTIFIER;

	public DealingLogicBase(BitFlyerAPIWrapper wrapper, SlackNotifier notifier, Map<String, Object> paramMap,
			Map<String, Object> settings) {
		super();
		this.WRAPPER = wrapper;
		this.NOTIFIER = notifier;
	}

	public void execute() {
	}
}
