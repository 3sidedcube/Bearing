package net.atomcode.bearing.location;

/**
 * Describes a location accuracy desired
 */
public enum Accuracy
{
	/**
	 * Within 2000 metres
	 */
	LOW(2000),
	/**
	 * Within 200 metres
	 */
	MEDIUM(200),
	/**
	 * Within 20 metres
	 */
	HIGH(20);

	public int value;

	Accuracy(int value)
	{
		this.value = value;
	}
}
