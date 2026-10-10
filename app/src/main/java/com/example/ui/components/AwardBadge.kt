package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.BibGourmandColor
import com.example.ui.theme.GreenStarColor
import com.example.ui.theme.MichelinGold
import com.example.ui.theme.MichelinRed
import com.example.ui.theme.SelectedColor

@Composable
fun AwardBadge(
    award: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val norm = award.trim()
    when {
        norm.contains("3 Star", ignoreCase = true) -> {
            ThreeStarsBadge(compact = compact, modifier = modifier)
        }

        norm.contains("2 Star", ignoreCase = true) -> {
            TwoStarsBadge(compact = compact, modifier = modifier)
        }

        norm.contains("1 Star", ignoreCase = true) -> {
            OneStarBadge(compact = compact, modifier = modifier)
        }

        norm.contains("Bib Gourmand", ignoreCase = true) -> {
            BibGourmandBadge(compact = compact, modifier = modifier)
        }

        else -> {
            SelectedBadge(compact = compact, modifier = modifier)
        }
    }
}

@Composable
fun ThreeStarsBadge(compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MichelinRed, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-1).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_michelin_star),
                    contentDescription = "Michelin Star",
                    tint = MichelinRed,
                    modifier = Modifier.size(if (compact) 10.dp else 12.dp)
                )
            }
        }
        if (!compact) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "3 Stars",
                color = MichelinGold,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TwoStarsBadge(compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MichelinRed, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-1).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_michelin_star),
                    contentDescription = "Michelin Star",
                    tint = MichelinRed,
                    modifier = Modifier.size(if (compact) 10.dp else 12.dp)
                )
            }
        }
        if (!compact) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "2 Stars",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OneStarBadge(compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MichelinRed, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_michelin_star),
            contentDescription = "Michelin Star",
            tint = MichelinRed,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp)
        )
        if (!compact) {
            Text(
                text = "1 Star",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BibGourmandBadge(compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, BibGourmandColor, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_bib_gourmand),
            contentDescription = "Bib Gourmand",
            tint = Color.Unspecified,
            modifier = Modifier.size(if (compact) 14.dp else 18.dp)
        )
        if (!compact) {
            Text(
                text = "Bib Gourmand",
                color = BibGourmandColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SelectedBadge(compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, SelectedColor, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Restaurant,
            contentDescription = "Selected",
            tint = SelectedColor,
            modifier = Modifier.size(if (compact) 14.dp else 18.dp)
        )
        if (!compact) {
            Text(
                text = "Selected",
                color = SelectedColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun GreenStarBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, GreenStarColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_green_star),
            contentDescription = "Green Star",
            tint = GreenStarColor,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = "Green Star",
            color = GreenStarColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}
