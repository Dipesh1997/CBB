package g.p.cbb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.ui.theme.OnWarningContainer
import g.p.cbb.ui.theme.Warning
import g.p.cbb.ui.theme.WarningContainer

@Composable
fun RiskWarningBanner(
    badDebtCount: Int,
    highOverdueCount: Int,
    modifier: Modifier = Modifier
) {
    if (badDebtCount <= 0 && highOverdueCount <= 0) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(color = WarningContainer, shape = RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = Warning.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Risk Warning",
                tint = Warning,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "Account Risk Alert",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = OnWarningContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                val alertText = buildString {
                    if (badDebtCount > 0) {
                        append("$badDebtCount account(s) flagged as Bad Debt. ")
                    }
                    if (highOverdueCount > 0) {
                        append("$highOverdueCount account(s) have overdue balances exceeding ₹10,000.")
                    }
                }
                Text(
                    text = alertText,
                    fontSize = 12.sp,
                    color = OnWarningContainer,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
