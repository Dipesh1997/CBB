package g.p.cbb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.ui.theme.Info
import g.p.cbb.ui.theme.InfoContainer
import g.p.cbb.ui.theme.OnInfoContainer

val LEDGER_TIPS = listOf(
    "Attach photo receipts for all transactions over ₹1,000 to keep digital proof and avoid balance disputes.",
    "Send monthly PDF statements to customers to encourage timely payment settlements.",
    "Flag defaulting or non-responsive customer accounts as 'Bad Debt' to keep active balance metrics clean.",
    "Use 'Record Part Payment' directly from customer ledgers to keep track of partial settlements against specific bills.",
    "Ensure customer phone numbers are accurate so bills can be easily shared via WhatsApp or SMS."
)

@Composable
fun TipsCard(
    modifier: Modifier = Modifier
) {
    var currentIndex by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(color = InfoContainer, shape = RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = Info.copy(alpha = 0.3f), shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Tip",
                        tint = Info,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Ledger Tip of the Day",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = OnInfoContainer
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            currentIndex = (currentIndex - 1 + LEDGER_TIPS.size) % LEDGER_TIPS.size
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Info.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Tip",
                            tint = Info,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            currentIndex = (currentIndex + 1) % LEDGER_TIPS.size
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Info.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Tip",
                            tint = Info,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = LEDGER_TIPS[currentIndex],
                fontSize = 12.sp,
                color = OnInfoContainer,
                lineHeight = 16.sp
            )
        }
    }
}
