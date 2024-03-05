package com.swmansion.starknet.data.types

import com.swmansion.starknet.data.serializers.HexToIntDeserializer
import com.swmansion.starknet.data.serializers.TransactionPolymorphicSerializer
import com.swmansion.starknet.data.serializers.TransactionReceiptPolymorphicSerializer
import com.swmansion.starknet.data.types.transactions.Transaction
import com.swmansion.starknet.data.types.transactions.TransactionExecutionStatus
import com.swmansion.starknet.data.types.transactions.TransactionReceipt
import com.swmansion.starknet.data.types.transactions.TransactionStatus
import com.swmansion.starknet.extensions.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.math.BigInteger
import kotlin.math.roundToInt

@Serializable
data class CallContractResponse(
    val result: List<Felt>,
)

@Serializable
data class InvokeFunctionResponse(
    @SerialName("transaction_hash") val transactionHash: Felt,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DeclareResponse(
    @JsonNames("transaction_hash")
    val transactionHash: Felt,

    @JsonNames("class_hash")
    val classHash: Felt,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DeployAccountResponse(
    @JsonNames("transaction_hash")
    val transactionHash: Felt,

    // TODO: (#344) deviation from the spec, make this non-nullable once Juno is updated
    @JsonNames("address", "contract_address")
    val address: Felt? = null,
)

@Serializable
data class EstimateFeeResponse(
    @SerialName("gas_consumed")
    val gasConsumed: Felt,

    @SerialName("gas_price")
    val gasPrice: Felt,

    @SerialName("data_gas_consumed")
    val dataGasConsumed: Felt,

    @SerialName("data_gas_price")
    val dataGasPrice: Felt,

    @SerialName("overall_fee")
    val overallFee: Felt,

    // TODO: (#344) Deviation from the spec, make this non-nullable once Pathfinder is updated
    @SerialName("unit")
    val feeUnit: PriceUnit? = null,
) {
    /**
     * Convert estimated fee to max fee with applied multiplier.
     *
     * Multiplies [overallFee] by round([multiplier] * 100%) and performs integer division by 100.
     *
     * @param multiplier Multiplier for max fee, defaults to 1.5.
     */
    fun toMaxFee(multiplier: Double = 1.5): Felt {
        require(multiplier >= 0)

        return overallFee.value.applyMultiplier(multiplier).toFelt
    }

    /**
     * Convert estimated fee to resource bounds with applied multipliers.
     *
     * Calculates max amount as maxAmount = [overallFee] / [gasPrice], unless [gasPrice] is 0, then maxAmount is 0.
     * Calculates max price per unit as maxPricePerUnit = [gasPrice].
     * Then multiplies maxAmount by round([amountMultiplier] * 100%) and maxPricePerUnit by round([unitPriceMultiplier] * 100%) and performs integer division by 100 on both.
     *
     * @param amountMultiplier Multiplier for max amount, defaults to 1.5.
     * @param unitPriceMultiplier Multiplier for max price per unit, defaults to 1.5.
     *
     * @return Resource bounds with applied multipliers.
     */
    fun toResourceBounds(
        amountMultiplier: Double = 1.5,
        unitPriceMultiplier: Double = 1.5,
    ): ResourceBoundsMapping {
        require(amountMultiplier >= 0)
        require(unitPriceMultiplier >= 0)

        val maxAmount = when (gasPrice) {
            Felt.ZERO -> Uint64.ZERO
            else -> (overallFee.value / gasPrice.value).applyMultiplier(amountMultiplier).toUint64
        }
        val maxPricePerUnit = gasPrice.value.applyMultiplier(unitPriceMultiplier).toUint128

        return ResourceBoundsMapping(
            l1Gas = ResourceBounds(maxAmount = maxAmount, maxPricePerUnit = maxPricePerUnit),
        )
    }
    private fun BigInteger.applyMultiplier(multiplier: Double): BigInteger {
        return (this * (multiplier * 100).roundToInt().toBigInteger()) / BigInteger.valueOf(100)
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GetBlockHashAndNumberResponse(
    @JsonNames("block_hash")
    val blockHash: Felt,

    @JsonNames("block_number")
    val blockNumber: Int,
)

@Serializable
data class GetTransactionStatusResponse(
    @SerialName("finality_status")
    val finalityStatus: TransactionStatus,

    @SerialName("execution_status")
    val executionStatus: TransactionExecutionStatus? = null,
)

@Serializable
sealed class Syncing {
    abstract val status: Boolean

    abstract val startingBlockHash: Felt

    abstract val startingBlockNumber: Int

    abstract val currentBlockHash: Felt

    abstract val currentBlockNumber: Int

    abstract val highestBlockHash: Felt

    abstract val highestBlockNumber: Int
}

@Serializable
data class NotSyncingResponse(
    override val status: Boolean,

    override val startingBlockHash: Felt = Felt.ZERO,

    override val startingBlockNumber: Int = 0,

    override val currentBlockHash: Felt = Felt.ZERO,

    override val currentBlockNumber: Int = 0,

    override val highestBlockHash: Felt = Felt.ZERO,

    override val highestBlockNumber: Int = 0,
) : Syncing()

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncingResponse(
    override val status: Boolean = true,

    @JsonNames("starting_block_hash")
    override val startingBlockHash: Felt,

    @Serializable(with = HexToIntDeserializer::class)
    @JsonNames("starting_block_num")
    override val startingBlockNumber: Int,

    @JsonNames("current_block_hash")
    override val currentBlockHash: Felt,

    @Serializable(with = HexToIntDeserializer::class)
    @JsonNames("current_block_num")
    override val currentBlockNumber: Int,

    @JsonNames("highest_block_hash")
    override val highestBlockHash: Felt,

    @Serializable(with = HexToIntDeserializer::class)
    @JsonNames("highest_block_num")
    override val highestBlockNumber: Int,
) : Syncing()

sealed interface Block {
    val timestamp: Int
    val sequencerAddress: Felt
    val parentHash: Felt
    val l1GasPrice: ResourcePrice
    val l1DataGasPrice: ResourcePrice
    val l1DataAvailabilityMode: L1DAMode
    val starknetVersion: String
}

/**
 * Represents a processed block.
 *
 * Corresponds to the `BLOCK_HEADER` schema defined in the JSON-RPC spec.
 */
sealed interface ProcessedBlock : Block {
    val status: BlockStatus
    val blockHash: Felt
    val blockNumber: Int
    val newRoot: Felt
}

/**
 * Represents a pending block.
 *
 * Corresponds to the `PENDING_BLOCK_HEADER` schema defined in the JSON-RPC spec.
 */
sealed interface PendingBlock : Block

@Serializable
sealed class BlockWithTransactions : Block {
    abstract val transactions: List<Transaction>
}

@Serializable
data class ProcessedBlockWithTransactions(
    @SerialName("status")
    override val status: BlockStatus,

    // Block body

    @SerialName("transactions")
    override val transactions: List<
        @Serializable(with = TransactionPolymorphicSerializer::class)
        Transaction,
        >,

    // Block header

    @SerialName("parent_hash")
    override val parentHash: Felt,

    @SerialName("block_hash")
    override val blockHash: Felt,

    @SerialName("block_number")
    override val blockNumber: Int,

    @SerialName("new_root")
    override val newRoot: Felt,

    @SerialName("timestamp")
    override val timestamp: Int,

    @SerialName("sequencer_address")
    override val sequencerAddress: Felt,

    @SerialName("l1_gas_price")
    override val l1GasPrice: ResourcePrice,

    @SerialName("l1_data_gas_price")
    override val l1DataGasPrice: ResourcePrice,

    @SerialName("l1_da_mode")
    override val l1DataAvailabilityMode: L1DAMode,

    @SerialName("starknet_version")
    override val starknetVersion: String,
) : BlockWithTransactions(), ProcessedBlock

@Serializable
data class PendingBlockWithTransactions(
    // Block body

    @SerialName("transactions")
    override val transactions: List<
        @Serializable(with = TransactionPolymorphicSerializer::class)
        Transaction,
        >,

    // Pending block header

    @SerialName("timestamp")
    override val timestamp: Int,

    @SerialName("sequencer_address")
    override val sequencerAddress: Felt,

    @SerialName("parent_hash")
    override val parentHash: Felt,

    @SerialName("l1_gas_price")
    override val l1GasPrice: ResourcePrice,

    @SerialName("l1_data_gas_price")
    override val l1DataGasPrice: ResourcePrice,

    @SerialName("l1_da_mode")
    override val l1DataAvailabilityMode: L1DAMode,

    @SerialName("starknet_version")
    override val starknetVersion: String,
) : BlockWithTransactions(), PendingBlock

@Serializable
data class TransactionWithReceipt(
    @Serializable(with = TransactionPolymorphicSerializer::class)
    @SerialName("transaction")
    val transaction: Transaction,

    @Serializable(with = TransactionReceiptPolymorphicSerializer::class)
    @SerialName("receipt")
    val receipt: TransactionReceipt,
)

@Serializable
sealed class BlockWithReceipts : Block {
    abstract val transactionWithReceipts: List<TransactionWithReceipt>
}

@Serializable
data class ProcessedBlockWithReceipts(
    @SerialName("status")
    override val status: BlockStatus,

    // Block body

    @SerialName("transactions")
    override val transactionWithReceipts: List<TransactionWithReceipt>,

    // Block header

    @SerialName("block_hash")
    override val blockHash: Felt,

    @SerialName("block_number")
    override val blockNumber: Int,

    @SerialName("new_root")
    override val newRoot: Felt,

    @SerialName("timestamp")
    override val timestamp: Int,

    @SerialName("sequencer_address")
    override val sequencerAddress: Felt,

    @SerialName("parent_hash")
    override val parentHash: Felt,

    @SerialName("l1_gas_price")
    override val l1GasPrice: ResourcePrice,

    @SerialName("l1_data_gas_price")
    override val l1DataGasPrice: ResourcePrice,

    @SerialName("l1_da_mode")
    override val l1DataAvailabilityMode: L1DAMode,

    @SerialName("starknet_version")
    override val starknetVersion: String,
) : BlockWithReceipts(), ProcessedBlock

@Serializable
data class PendingBlockWithReceipts(
    // Block body

    @SerialName("transactions")
    override val transactionWithReceipts: List<TransactionWithReceipt>,

    // Pending block header

    @SerialName("timestamp")
    override val timestamp: Int,

    @SerialName("sequencer_address")
    override val sequencerAddress: Felt,

    @SerialName("parent_hash")
    override val parentHash: Felt,

    @SerialName("l1_gas_price")
    override val l1GasPrice: ResourcePrice,

    @SerialName("l1_data_gas_price")
    override val l1DataGasPrice: ResourcePrice,

    @SerialName("l1_da_mode")
    override val l1DataAvailabilityMode: L1DAMode,

    @SerialName("starknet_version")
    override val starknetVersion: String,
) : BlockWithReceipts(), PendingBlock

@Serializable
sealed class BlockWithTransactionHashes : Block {
    abstract val transactionHashes: List<Felt>
}

@Serializable
data class ProcessedBlockWithTransactionHashes(
    @SerialName("status")
    override val status: BlockStatus,

    // Block body

    @SerialName("transactions")
    override val transactionHashes: List<Felt>,

    // Block header

    @SerialName("block_hash")
    override val blockHash: Felt,

    @SerialName("block_number")
    override val blockNumber: Int,

    @SerialName("new_root")
    override val newRoot: Felt,

    @SerialName("timestamp")
    override val timestamp: Int,

    @SerialName("sequencer_address")
    override val sequencerAddress: Felt,

    @SerialName("parent_hash")
    override val parentHash: Felt,

    @SerialName("l1_gas_price")
    override val l1GasPrice: ResourcePrice,

    @SerialName("l1_data_gas_price")
    override val l1DataGasPrice: ResourcePrice,

    @SerialName("l1_da_mode")
    override val l1DataAvailabilityMode: L1DAMode,

    @SerialName("starknet_version")
    override val starknetVersion: String,
) : BlockWithTransactionHashes(), ProcessedBlock

@Serializable
data class PendingBlockWithTransactionHashes(
    // Block body

    @SerialName("transactions")
    override val transactionHashes: List<Felt>,

    // Pending block header

    @SerialName("timestamp")
    override val timestamp: Int,

    @SerialName("sequencer_address")
    override val sequencerAddress: Felt,

    @SerialName("parent_hash")
    override val parentHash: Felt,

    @SerialName("l1_gas_price")
    override val l1GasPrice: ResourcePrice,

    @SerialName("l1_data_gas_price")
    override val l1DataGasPrice: ResourcePrice,

    @SerialName("l1_da_mode")
    override val l1DataAvailabilityMode: L1DAMode,

    @SerialName("starknet_version")
    override val starknetVersion: String,
) : BlockWithTransactionHashes(), PendingBlock

@Serializable
data class StorageEntries(
    @SerialName("key")
    val key: Felt,

    @SerialName("value")
    val value: Felt,
)

@Serializable
data class StorageDiffItem(
    @SerialName("address")
    val address: Felt,

    @SerialName("storage_entries")
    val storageEntries: List<StorageEntries>,
)

@Serializable
data class DeployedContractItem(
    @SerialName("address")
    val address: Felt,

    @SerialName("class_hash")
    val classHash: Felt,
)

@Serializable
data class NonceItem(
    @SerialName("contract_address")
    val contractAddress: Felt,

    @SerialName("nonce")
    val nonce: Felt,
)

@Serializable
data class StateDiff(
    @SerialName("storage_diffs")
    val storageDiffs: List<StorageDiffItem>,

    @SerialName("deprecated_declared_classes")
    val deprecatedDeclaredClasses: List<Felt>,

    @SerialName("declared_classes")
    val declaredClasses: List<DeclaredClassItem>,

    @SerialName("deployed_contracts")
    val deployedContracts: List<DeployedContractItem>,

    @SerialName("replaced_classes")
    val replacedClasses: List<ReplacedClassItem>,

    @SerialName("nonces")
    val nonces: List<NonceItem>,
)

@Serializable
data class DeclaredClassItem(
    @SerialName("class_hash")
    val classHash: Felt,

    @SerialName("compiled_class_hash")
    val compiledClassHash: Felt,
)

@Serializable
data class ReplacedClassItem(
    @SerialName("contract_address")
    val address: Felt,

    @SerialName("class_hash")
    val classHash: Felt,
)

@Serializable
sealed class StateUpdate {
    abstract val oldRoot: Felt
    abstract val stateDiff: StateDiff
}

@Serializable
data class StateUpdateResponse(
    @SerialName("block_hash")
    val blockHash: Felt,

    @SerialName("new_root")
    val newRoot: Felt,

    @SerialName("old_root")
    override val oldRoot: Felt,

    @SerialName("state_diff")
    override val stateDiff: StateDiff,
) : StateUpdate()

@Serializable
data class PendingStateUpdateResponse(
    @SerialName("old_root")
    override val oldRoot: Felt,

    @SerialName("state_diff")
    override val stateDiff: StateDiff,
) : StateUpdate()

// TODO: remove SCREAMING_SNAKE_CASE @JsonNames once devnet is updated
@Suppress("DataClassPrivateConstructor")
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResourceBoundsMapping private constructor(
    @SerialName("l1_gas")
    @JsonNames("L1_GAS")
    val l1Gas: ResourceBounds,

    @SerialName("l2_gas")
    @JsonNames("L2_GAS")
    val l2Gas: ResourceBounds,
) {
    constructor(
        l1Gas: ResourceBounds,
    ) : this(
        // As of Starknet 0.13.0, the L2 gas is not supported
        // Because of this, the L2 gas values are hardcoded to 0
        l1Gas = l1Gas,
        l2Gas = ResourceBounds.ZERO,
    )
}

@Serializable
data class ResourceBounds(
    @SerialName("max_amount")
    val maxAmount: Uint64,

    @SerialName("max_price_per_unit")
    val maxPricePerUnit: Uint128,
) {
    companion object {
        @field:JvmField
        val ZERO = ResourceBounds(Uint64.ZERO, Uint128.ZERO)
    }

    fun toMaxFee(): Felt {
        return maxAmount.value.multiply(maxPricePerUnit.value).toFelt
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResourcePrice(
    // TODO: (#344) This is a deviation from the spec, make this non-nullable once Juno is updated
    @SerialName("price_in_wei")
    val priceInWei: Felt? = null,

    @SerialName("price_in_fri")
    @JsonNames("price_in_strk") // TODO: (#344) RPC 0.5.0 legacy name, remove once Pathfinder is updated
    val priceInFri: Felt,
)

@Serializable
data class FeePayment(
    @SerialName("amount")
    val amount: Felt,

    @SerialName("unit")
    val unit: PriceUnit,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class PriceUnit {
    @SerialName("WEI")
    WEI,

    @SerialName("FRI")
    @JsonNames("STRK") // TODO: (#344) RPC 0.5.0 legacy name, remove once Pathfinder is updated
    FRI,
}

@Serializable
enum class L1DAMode {
    @SerialName("BLOB")
    BLOB,

    @SerialName("CALLDATA")
    CALLDATA,
}
