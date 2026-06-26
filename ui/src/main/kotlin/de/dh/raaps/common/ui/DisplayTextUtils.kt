package de.dh.raaps.common.ui

import android.content.res.Resources

object DisplayTextUtils {
    /**
     * Plural string treatment with fixed support of the string for quantity 0.
     * Works around android's implementation which uses CLDR plural rules which make the {@code "zero"}
     * quantity string is never used; instead, for quantity 0, the {@code "other"} string is used.
     * To work around this, the caller must provide the {@code "zero"} quantity string as separate string resource.
     * <p>
     * <b>Example XML declaration:</b>
     * <pre>{@code
     * <resources>
     *     <!-- The 'zero' item in this plurals resource is ignored by the system for quantity 0 on English and German locales -->
     *     <plurals name="n_items_available">
     *         <item quantity="zero">@string/no_items_available</item>
     *         <item quantity="one">1 item available</item>
     *         <item quantity="other">%d items available</item>
     *     </plurals>
     *
     *     <!-- This separate string is used as a workaround -->
     *     <string name="no_items_available">No items available</string>
     * </resources>
     *
     * // Call in code:
     * DisplayTextUtils.getQuantityStringZero(
     *     resources,
     *     R.plurals.n_items_available,
     *     R.string.no_items_available,
     *     0
     * ); // returns "No items available"
     *
     * DisplayTextUtils.getQuantityStringZero(
     *     resources,
     *     R.plurals.n_items_available,
     *     R.string.no_items_available,
     *     5
     * ); // returns "5 items available"
     * }</pre>
     * @param resources The resources to get the strings from.
     * @param pluralsStringResId The resource id of the plurals string. The {@code "zero"} item is not used for english but should
     * yet have the same contents as the separate string for the {@code "zero"} item, provided in parameter {@code "zeroStringResId"}.
     * @param zeroStringResId The resource id of the string providing the {@code "zero"} string contents.
     * @param quantity The quantity to use as selector.
     * @return Correct quantity string, depending on quantity.
     */
    fun getQuantityStringZero(
        resources: Resources,
        pluralsStringResId: Int,
        zeroStringResId: Int,
        quantity: Int,
        vararg formatArgs: Any
    ): String {
        return if (quantity == 0) {
            resources.getString(zeroStringResId, *formatArgs)
        } else {
            if (formatArgs.isEmpty()) {
                resources.getQuantityString(pluralsStringResId, quantity, quantity)
            } else {
                resources.getQuantityString(pluralsStringResId, quantity, *formatArgs)
            }
        }
    }
}