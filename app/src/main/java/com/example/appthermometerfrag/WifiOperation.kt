package com.example.appthermometerfrag

enum class WifiOperation {
    CONNECT_TO_AP,
    DISCONNECT_FROM_AP,
    OPEN_SOCKET,
    TRANSACTION,
    RECEIVE
}


enum class WifiEvent {
    CONNECT_TO_AP,
    DISCONNECT_FROM_AP,
    NEW_AP_CREATED,
    NEW_AP_ERROR,
    CONNECT_TO_AP_ERROR,
    NETWORK_EVENT
}

enum class OperationState {
    START,
    PROGRESS,
    FINISH
}
