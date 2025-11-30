package com.cobblehunts.utils

import java.util.UUID

sealed class HuntSelection(open val uuid: UUID)
data class PartySelection(val partyIndex: Int, override val uuid: UUID) : HuntSelection(uuid)
data class PcSelection(val boxIndex: Int, val pcSlot: Int, override val uuid: UUID) : HuntSelection(uuid)