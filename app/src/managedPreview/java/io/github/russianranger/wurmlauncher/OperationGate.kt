package io.github.russianranger.wurmlauncher

import java.util.concurrent.atomic.AtomicInteger

/** Cross-session maintenance reservation. No lock-order coupling to the session monitors. */
object OperationGate {
    private val owners=AtomicInteger(0)
    @Volatile var recoveryError: String?=null
    val maintenance get()=owners.get()<0
    fun enter(): Boolean {
        if(recoveryError!=null) return false
        while(true) { val n=owners.get(); if(n<0) return false; if(owners.compareAndSet(n,n+1)) return true }
    }
    fun leave() { check(owners.decrementAndGet()>=0) }
    fun beginMaintenance()=owners.compareAndSet(0,-1)
    fun endMaintenance() { check(owners.compareAndSet(-1,0)) }
}
