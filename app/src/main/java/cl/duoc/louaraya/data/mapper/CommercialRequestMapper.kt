package cl.duoc.louaraya.data.mapper

import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus

fun CommercialRequestEntity.toDomain(): CommercialRequest = CommercialRequest(
    id = id,
    productId = productId,
    date = date,
    intention = RequestIntention.valueOf(intention),
    anonymizedClient = anonymizedClient,
    comment = comment,
    total = total,
    status = RequestStatus.valueOf(status),
    campaignCategory = campaignCategory?.let(CampaignCategory::valueOf),
    ownerId = ownerId, customization = customization, campaignId = campaignId,
    sourceCollectionId = sourceCollectionId, journeyId = journeyId, agreedTotal = agreedTotal, agreedAt = agreedAt, quantity = quantity
)

fun CommercialRequest.toEntity(): CommercialRequestEntity = CommercialRequestEntity(
    id = id,
    productId = productId,
    date = date,
    intention = intention.name,
    anonymizedClient = anonymizedClient,
    comment = comment,
    total = total,
    status = status.name,
    campaignCategory = campaignCategory?.name,
    ownerId = ownerId, customization = customization, campaignId = campaignId,
    sourceCollectionId = sourceCollectionId, journeyId = journeyId, agreedTotal = agreedTotal, agreedAt = agreedAt, quantity = quantity
)
