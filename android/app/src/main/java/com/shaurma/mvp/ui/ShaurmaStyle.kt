package com.shaurma.mvp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ShaurmaBlack = Color.Black
val ShaurmaWhite = Color.White
val ShaurmaPlaceholderGray = Color(0xFF888888)
val ShaurmaTextGray = Color(0xFFAAAAAA)

@Composable
fun rememberShaurmaFontFamily(): FontFamily {
    val context = LocalContext.current
    val fontResId = remember(context) {
        context.resources.getIdentifier("mtcorsva", "font", context.packageName)
    }
    return remember(fontResId) {
        if (fontResId != 0) FontFamily(Font(fontResId)) else FontFamily.Cursive
    }
}

@Composable
fun ShaurmaTopZone(
    topZoneHeight: Dp,
    leftIconName: String,
    leftContentDescription: String,
    onLeftClick: () -> Unit,
    rightIconName: String,
    rightContentDescription: String,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconSize = topZoneHeight * 0.8f
    val iconPadding = topZoneHeight * 0.1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topZoneHeight)
            .background(ShaurmaBlack)
            .padding(horizontal = iconPadding, vertical = iconPadding),
    ) {
        ShaurmaIconButton(
            iconName = leftIconName,
            contentDescription = leftContentDescription,
            onClick = onLeftClick,
            modifier = Modifier.align(Alignment.CenterStart),
            size = iconSize,
        )
        ShaurmaIconButton(
            iconName = rightIconName,
            contentDescription = rightContentDescription,
            onClick = onRightClick,
            modifier = Modifier.align(Alignment.CenterEnd),
            size = iconSize,
        )
    }
}

@Composable
private fun ShaurmaIconButton(
    iconName: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    size: Dp,
) {
    val context = LocalContext.current
    val iconResId = remember(iconName, context) {
        context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
    val fontFamily = rememberShaurmaFontFamily()
    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (iconResId != 0) {
            Image(
                painter = painterResource(iconResId),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = if (iconName == "home") "⌂" else "□",
                color = ShaurmaWhite,
                fontFamily = fontFamily,
                fontSize = (size.value * 0.55f).sp,
            )
        }
    }
}

@Composable
fun ShaurmaOutlinedMenuButton(
    text: String,
    width: Dp,
    height: Dp,
    fontSize: TextUnit,
    onFontOverflow: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fontFamily = rememberShaurmaFontFamily()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, ShaurmaWhite),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ShaurmaBlack,
            contentColor = ShaurmaWhite,
            disabledContainerColor = ShaurmaBlack,
            disabledContentColor = ShaurmaTextGray,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
            .width(width)
            .height(height),
    ) {
        Text(
            text = text.uppercase(),
            color = ShaurmaWhite,
            fontFamily = fontFamily,
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            onTextLayout = { result ->
                if (result.hasVisualOverflow) onFontOverflow()
            },
        )
    }
}

@Composable
fun ShaurmaProfileTextField(
    value: String,
    placeholder: String,
    width: Dp,
    height: Dp,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val fontFamily = rememberShaurmaFontFamily()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .width(width)
            .height(height),
        textStyle = TextStyle(
            color = ShaurmaWhite,
            fontFamily = fontFamily,
            fontSize = 16.sp,
        ),
        placeholder = {
            Text(
                text = placeholder,
                color = ShaurmaPlaceholderGray,
                fontFamily = fontFamily,
                fontSize = 16.sp,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ShaurmaWhite,
            unfocusedTextColor = ShaurmaWhite,
            cursorColor = ShaurmaWhite,
            focusedBorderColor = ShaurmaWhite,
            unfocusedBorderColor = ShaurmaWhite,
            focusedContainerColor = ShaurmaBlack,
            unfocusedContainerColor = ShaurmaBlack,
            disabledContainerColor = ShaurmaBlack,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

