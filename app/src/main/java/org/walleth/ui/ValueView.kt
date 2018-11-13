package org.walleth.ui

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.value.view.*
import org.ligi.kaxt.doAfterEdit
import org.ligi.kaxt.setVisibility
import org.ligi.kaxtui.alert
import org.walleth.R
import org.walleth.data.config.Settings
import org.walleth.data.exchangerate.ExchangeRateProvider
import org.walleth.data.tokens.Token
import org.walleth.data.tokens.isETH
import org.walleth.functions.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.BigInteger.ZERO
import java.text.ParseException

enum class ValueSource {
    TOKEN,FIAT
}
class ValueViewModel(private val valueView: ValueView,
                     private val exchangeRateProvider: ExchangeRateProvider,
                     private val settings: Settings) {

    var currentValue = ZERO
    private var currentFiatValue: BigDecimal? = null
    private var currentToken: Token? = null
    private var valueSource = ValueSource.TOKEN

    private fun getAmountFromETHString(amount: String) = try {
        (amount.removePrefix("~").asBigDecimal() * BigDecimal("1" + currentToken!!.decimalsInZeroes())).toBigInteger()
    } catch (e: ParseException) {
        ZERO
    }


    init {

        valueView.current_eth.doAfterEdit { editable ->
            if (valueView.current_eth.isFocused) {
                currentToken?.let { token ->
                    setValue(getAmountFromETHString(editable.toString()), token)
                }
            }
        }


        valueView.current_fiat.doAfterEdit { editable ->
            if (valueView.current_fiat.isFocused) {
                setFiatValue(BigDecimal(editable.toString().removePrefix("~")))
            }
        }


        // only intercept touch through click listener if view can show precise
        if (valueView.showsPrecise && !valueView.allowEdit) {

            valueView.current_eth.setOnClickListener {
                currentToken?.let { tokenNotNull ->
                    if (valueView.current_eth.text.isValueImprecise()) {
                        showPreciseAmountAlert(currentValue.toFullValueString(tokenNotNull) + valueView.current_token_symbol.text)
                    }
                }
            }

            valueView.current_fiat.setOnClickListener {
                currentFiatValue?.let { currentExchangeValueNotNull ->
                    if (valueView.current_fiat.text.isValueImprecise()) {
                        showPreciseAmountAlert(String.format("%f", currentExchangeValueNotNull) + valueView.current_fiat_symbol.text)
                    }
                }
            }


        }

    }


    private fun showPreciseAmountAlert(fullAmountString: String) =
            valueView.context.alert(fullAmountString, valueView.context.getString(R.string.precise_amount_alert_title))


    fun setValue(value: BigInteger, token: Token) {

        if (value == currentValue && token == currentToken) {
            return
        }
        if (token.isETH()) {
            this.currentFiatValue = exchangeRateProvider.convertToFiat(value, settings.currentFiat)
        }

        currentValue = value
        currentToken = token

        refresh()
    }


    private fun setFiatValue(value: BigDecimal?) {

        if (currentFiatValue == value) {
            return
        }

        if (currentToken?.isETH() == true) {
            currentFiatValue = value
            currentValue = exchangeRateProvider.convertFromFiat(currentFiatValue, settings.currentFiat)
        }

        refresh()
    }

    private fun refresh() {
        currentToken?.let { currentToken ->
            valueView.current_token_symbol.text = currentToken.symbol

            val valueString = currentValue.toValueString(currentToken)
            if (valueView.current_eth.text.toString() != valueString) {
                valueView.current_eth.setText(valueString)
            }
        }

        valueView.current_fiat_symbol.setVisibility(currentToken?.isETH() == true)
        valueView.current_fiat.setVisibility(currentToken?.isETH() == true)


        valueView.current_fiat_symbol.text = settings.currentFiat
        val newFiatText = if (currentFiatValue != null) {
            twoDigitDecimalFormat.format(currentFiatValue).addPrefixOnCondition(prefix = "~", condition = currentFiatValue?.scale() ?: 0 > 2)
        } else {
            "?"
        }
        if (valueView.current_fiat.text.toString() != newFiatText) {
            valueView.current_fiat.setText(newFiatText)
        }

    }

}

open class ValueView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    open val layoutRes = R.layout.value
    val showsPrecise: Boolean
    val allowEdit: Boolean

    init {
        // extract the showPrecise value
        val a: TypedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.ValueView,
                0, 0)
        try {
            showsPrecise = a.getBoolean(R.styleable.ValueView_showPrecise, true)
            allowEdit = a.getBoolean(R.styleable.ValueView_allowEdit, false)
        } finally {
            a.recycle()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(layoutRes, this, true)

        if (!allowEdit) {
            current_eth.keyListener = null
            current_fiat.keyListener = null
        }
        current_eth.isCursorVisible = allowEdit
        current_eth.isFocusableInTouchMode = allowEdit
        current_eth.setBackgroundDrawable(null)

        current_fiat.isEnabled = allowEdit

        current_fiat.isFocusableInTouchMode = allowEdit
        current_fiat.setBackgroundDrawable(null)

    }

}