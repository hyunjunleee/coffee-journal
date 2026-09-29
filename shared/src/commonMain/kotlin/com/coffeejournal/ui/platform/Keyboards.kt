package com.coffeejournal.ui.platform

import androidx.compose.ui.text.input.KeyboardType

/**
 * The keyboard for a number field that also takes separators: 재배 고도 ("1900-2100", "1,950") and the Heirloom
 * numbers ("74112, 74158"). Android's number keyboards have the minus, the comma, the point and a space; iOS's number
 * pad has digits only, so there the full keyboard opens and the field's input filter keeps the digits and separators.
 */
expect val NumbersWithSeparatorsKeyboard: KeyboardType
