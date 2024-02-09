package com.swmansion.starknet.data

import com.swmansion.starknet.crypto.Poseidon
import com.swmansion.starknet.crypto.StarknetCurve
import com.swmansion.starknet.data.types.*
import com.swmansion.starknet.data.types.transactions.DAMode
import com.swmansion.starknet.data.types.transactions.TransactionType
import com.swmansion.starknet.extensions.toFelt
import com.swmansion.starknet.provider.Provider

/**
 * Toolkit for calculating hashes of transactions.
 *
 * To obtain the chain id, [Provider.getChainId] can be used. Alternatively, the chain id can be encoded from the network name using [Felt.fromShortString], e.g. `Felt.fromShortString("SN_SEPOLIA")`.
 */
object TransactionHashCalculator {
    private val l1GasPrefix by lazy { Felt.fromShortString("L1_GAS") }
    private val l2GasPrefix by lazy { Felt.fromShortString("L2_GAS") }

    @JvmStatic
    fun calculateInvokeTxV1Hash(
        contractAddress: Felt,
        calldata: Calldata,
        chainId: Felt,
        version: Felt,
        nonce: Felt,
        maxFee: Felt,
    ): Felt = transactionHashCommon(
        txType = TransactionType.INVOKE,
        version = version,
        contractAddress = contractAddress,
        entryPointSelector = Felt.ZERO,
        calldata = calldata,
        maxFee = maxFee,
        chainId = chainId,
        nonce = nonce,
    )

    @JvmStatic
    fun calculateInvokeTxV3Hash(
        senderAddress: Felt,
        calldata: Calldata,
        chainId: Felt,
        version: Felt,
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
    fun calculateDeployAccountV1TxHash(
        classHash: Felt,
        calldata: Calldata,
        salt: Felt,
        chainId: Felt,
        version: Felt,
        maxFee: Felt,
        nonce: Felt,
    ): Felt {
        val contractAddress = ContractAddressCalculator.calculateAddressFromHash(
            classHash = classHash,
            calldata = calldata,
            salt = salt,
        )
        return transactionHashCommon(
            txType = TransactionType.DEPLOY_ACCOUNT,
            version = version,
            contractAddress = contractAddress,
            entryPointSelector = Felt.ZERO,
            calldata = listOf(classHash, salt, *calldata.toTypedArray()),
            maxFee = maxFee,
            chainId = chainId,
            nonce = nonce,
        )
    }

    @JvmStatic
    fun calculateDeployAccountV3TxHash(
        classHash: Felt,
        constructorCalldata: Calldata,
        salt: Felt,
        paymasterData: PaymasterData,
        chainId: Felt,
        version: Felt,
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
    fun calculateDeclareV1TxHash(
        classHash: Felt,
        chainId: Felt,
        senderAddress: Felt,
        maxFee: Felt,
        version: Felt,
        nonce: Felt,
    ): Felt {
        val hash = StarknetCurve.pedersenOnElements(listOf(classHash))
        return StarknetCurve.pedersenOnElements(
            TransactionType.DECLARE.txPrefix,
            version,
            senderAddress,
            Felt.ZERO,
            hash,
            maxFee,
            chainId,
            nonce,
        )
    }

    @JvmStatic
    fun calculateDeclareV2TxHash(
        classHash: Felt,
        chainId: Felt,
        senderAddress: Felt,
        maxFee: Felt,
        version: Felt,
        nonce: Felt,
        compiledClassHash: Felt,
    ): Felt {
        val calldataHash = StarknetCurve.pedersenOnElements(listOf(classHash))
        return StarknetCurve.pedersenOnElements(
            TransactionType.DECLARE.txPrefix,
            version,
            senderAddress,
            Felt.ZERO,
            calldataHash,
            maxFee,
            chainId,
            nonce,
            compiledClassHash,
        )
    }

    @JvmStatic
    fun calculateDeclareV3TxHash(
        classHash: Felt,
        chainId: Felt,
        senderAddress: Felt,
        version: Felt,
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

    private fun transactionHashCommon(
        txType: TransactionType,
        version: Felt,
        contractAddress: Felt,
        entryPointSelector: Felt,
        calldata: Calldata,
        maxFee: Felt,
        chainId: Felt,
        nonce: Felt,
    ): Felt {
        return StarknetCurve.pedersenOnElements(
            txType.txPrefix,
            version,
            contractAddress,
            entryPointSelector,
            StarknetCurve.pedersenOnElements(calldata),
            maxFee,
            chainId,
            nonce,
        )
    }

    private fun prepareCommonTransanctionV3Fields(
        txType: TransactionType,
        version: Felt,
        address: Felt,
        tip: Uint64,
        resourceBounds: ResourceBoundsMapping,
        paymasterData: PaymasterData,
        chainId: Felt,
        nonce: Felt,
        nonceDataAvailabilityMode: DAMode,
        feeDataAvailabilityMode: DAMode,
    ): List<Felt> {
        return listOf(
            txType.txPrefix,
            version,
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

    private fun prepareResourceBoundsForFee(resourceBounds: ResourceBoundsMapping): Pair<Felt, Felt> {
        val l1GasBound = l1GasPrefix.value.shiftLeft(64 + 128)
            .add(resourceBounds.l1Gas.maxAmount.value.shiftLeft(128))
            .add(resourceBounds.l1Gas.maxPricePerUnit.value)
            .toFelt
        val l2GasBound = l2GasPrefix.value.shiftLeft(64 + 128)
            .add(resourceBounds.l2Gas.maxAmount.value.shiftLeft(128))
            .add(resourceBounds.l2Gas.maxPricePerUnit.value)
            .toFelt

        return l1GasBound to l2GasBound
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
