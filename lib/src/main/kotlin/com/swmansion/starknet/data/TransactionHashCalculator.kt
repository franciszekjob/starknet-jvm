package com.swmansion.starknet.data

import com.swmansion.starknet.crypto.Poseidon
import com.swmansion.starknet.data.types.*
import com.swmansion.starknet.data.types.DAMode
import com.swmansion.starknet.data.types.TransactionType
import com.swmansion.starknet.data.types.TransactionVersion
import com.swmansion.starknet.extensions.toFelt

/**
 * Toolkit for calculating hashes of transactions.
 */
object TransactionHashCalculator {
    private val l1GasPrefix by lazy { Felt.fromShortString("L1_GAS") }
    private val l2GasPrefix by lazy { Felt.fromShortString("L2_GAS") }
    private val l1DataGasPrefix by lazy { Felt.fromShortString("L1_DATA") }

    @JvmStatic
    fun calculateInvokeTxV3Hash(
        senderAddress: Felt,
        calldata: Calldata,
        chainId: StarknetChainId,
        version: TransactionVersion,
        nonce: Felt,
        tip: Uint64,
        resourceBounds: ResourceBoundsMapping,
        paymasterData: PaymasterData,
        accountDeploymentData: AccountDeploymentData,
        feeDataAvailabilityMode: DAMode,
        nonceDataAvailabilityMode: DAMode,
    ): Felt {
        return Poseidon.poseidonHash(
            *prepareCommonTransanctionV3Fields(
                txType = TransactionType.INVOKE,
                version = version,
                address = senderAddress,
                tip = tip,
                resourceBounds = resourceBounds,
                paymasterData = paymasterData,
                chainId = chainId,
                nonce = nonce,
                nonceDataAvailabilityMode = nonceDataAvailabilityMode,
                feeDataAvailabilityMode = feeDataAvailabilityMode,
            ).toTypedArray(),
            Poseidon.poseidonHash(accountDeploymentData),
            Poseidon.poseidonHash(calldata),
        )
    }

    @JvmStatic
    fun calculateDeployAccountV3TxHash(
        classHash: Felt,
        constructorCalldata: Calldata,
        salt: Felt,
        paymasterData: PaymasterData,
        chainId: StarknetChainId,
        version: TransactionVersion,
        nonce: Felt,
        tip: Uint64,
        resourceBounds: ResourceBoundsMapping,
        feeDataAvailabilityMode: DAMode,
        nonceDataAvailabilityMode: DAMode,
    ): Felt {
        val contractAddress = ContractAddressCalculator.calculateAddressFromHash(
            classHash = classHash,
            calldata = constructorCalldata,
            salt = salt,
        )
        return Poseidon.poseidonHash(
            *prepareCommonTransanctionV3Fields(
                txType = TransactionType.DEPLOY_ACCOUNT,
                version = version,
                address = contractAddress,
                tip = tip,
                resourceBounds = resourceBounds,
                paymasterData = paymasterData,
                chainId = chainId,
                nonce = nonce,
                nonceDataAvailabilityMode = nonceDataAvailabilityMode,
                feeDataAvailabilityMode = feeDataAvailabilityMode,
            ).toTypedArray(),
            Poseidon.poseidonHash(constructorCalldata),
            classHash,
            salt,
        )
    }

    @JvmStatic
    fun calculateDeclareV3TxHash(
        classHash: Felt,
        chainId: StarknetChainId,
        senderAddress: Felt,
        version: TransactionVersion,
        nonce: Felt,
        compiledClassHash: Felt,
        tip: Uint64,
        resourceBounds: ResourceBoundsMapping,
        paymasterData: PaymasterData,
        accountDeploymentData: AccountDeploymentData,
        feeDataAvailabilityMode: DAMode,
        nonceDataAvailabilityMode: DAMode,
    ): Felt {
        return Poseidon.poseidonHash(
            *prepareCommonTransanctionV3Fields(
                txType = TransactionType.DECLARE,
                version = version,
                address = senderAddress,
                tip = tip,
                resourceBounds = resourceBounds,
                paymasterData = paymasterData,
                chainId = chainId,
                nonce = nonce,
                nonceDataAvailabilityMode = nonceDataAvailabilityMode,
                feeDataAvailabilityMode = feeDataAvailabilityMode,
            ).toTypedArray(),
            Poseidon.poseidonHash(accountDeploymentData),
            classHash,
            compiledClassHash,
        )
    }

    private fun prepareCommonTransanctionV3Fields(
        txType: TransactionType,
        version: TransactionVersion,
        address: Felt,
        tip: Uint64,
        resourceBounds: ResourceBoundsMapping,
        paymasterData: PaymasterData,
        chainId: StarknetChainId,
        nonce: Felt,
        nonceDataAvailabilityMode: DAMode,
        feeDataAvailabilityMode: DAMode,
    ): List<Felt> {
        return listOf(
            txType.txPrefix,
            version.value,
            address,
            Poseidon.poseidonHash(
                tip.toFelt,
                *prepareResourceBoundsForFee(resourceBounds).toList().toTypedArray(),
            ),
            Poseidon.poseidonHash(paymasterData),
            chainId.value.toFelt,
            nonce,
            prepareDataAvailabilityModes(
                feeDataAvailabilityMode,
                nonceDataAvailabilityMode,
            ),
        )
    }

    private fun prepareResourceBoundsForFee(resourceBounds: ResourceBoundsMapping): List<Felt> {
        val l1GasBound = l1GasPrefix.value.shiftLeft(64 + 128)
            .add(resourceBounds.l1Gas.maxAmount.value.shiftLeft(128))
            .add(resourceBounds.l1Gas.maxPricePerUnit.value)
            .toFelt
        val l2GasBound = l2GasPrefix.value.shiftLeft(64 + 128)
            .add(resourceBounds.l2Gas.maxAmount.value.shiftLeft(128))
            .add(resourceBounds.l2Gas.maxPricePerUnit.value)
            .toFelt
        val l1DataGasBound = l1DataGasPrefix.value.shiftLeft(64 + 128)
            .add(resourceBounds.l1DataGas.maxAmount.value.shiftLeft(128))
            .add(resourceBounds.l1DataGas.maxPricePerUnit.value)
            .toFelt

        return listOf(l1GasBound, l2GasBound, l1DataGasBound)
    }

    internal fun prepareDataAvailabilityModes(
        feeDataAvailabilityMode: DAMode,
        nonceDataAvailabilityMode: DAMode,
    ): Felt {
        return nonceDataAvailabilityMode.value.toBigInteger().shiftLeft(32)
            .add(feeDataAvailabilityMode.value.toBigInteger())
            .toFelt
    }
}
