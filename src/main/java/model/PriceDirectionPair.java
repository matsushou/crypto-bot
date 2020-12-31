package model;

public class PriceDirectionPair {
	private final int price;
	private final DirectionEnum direction;

	public PriceDirectionPair(int price, DirectionEnum direction) {
		this.price = price;
		this.direction = direction;
	}

	public int getPrice() {
		return price;
	}

	public DirectionEnum getDirection() {
		return direction;
	}
}
