package com.arkivanov.mvikotlin.timetravel.client.internal

import com.arkivanov.mvikotlin.timetravel.client.internal.TimeTravelClientStoreFactory.Connector
import com.arkivanov.mvikotlin.timetravel.proto.internal.data.timetravelstateupdate.TimeTravelStateUpdate
import com.arkivanov.mvikotlin.timetravel.proto.internal.io.ReaderThread
import com.arkivanov.mvikotlin.timetravel.proto.internal.io.WriterThread
import com.badoo.reaktive.disposable.Disposable
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.ObservableEmitter
import com.badoo.reaktive.observable.observable
import com.badoo.reaktive.observable.observeOn
import com.badoo.reaktive.observable.onErrorReturn
import com.badoo.reaktive.observable.subscribeOn
import com.badoo.reaktive.scheduler.ioScheduler
import com.badoo.reaktive.scheduler.mainScheduler
import java.net.Socket

internal class TimeTravelConnector(
    private val host: String,
    private val port: Int
) : Connector {

    override fun connect(): Observable<Connector.Event> =
        observable<Connector.Event> { it.connect() }
            .onErrorReturn { Connector.Event.Error(it.message) }
            .subscribeOn(ioScheduler)
            .observeOn(mainScheduler)

    private fun ObservableEmitter<Connector.Event>.connect() {
        val socket = Socket(host, port)

        if (isDisposed) {
            socket.closeSafe()
            return
        }

        val reader =
            ReaderThread<TimeTravelStateUpdate>(
                socket = socket,
                onRead = { onNext(Connector.Event.StateUpdate(it)) },
                onError = ::onError
            )

        val writer = WriterThread(socket = socket, onError = ::onError)

        onNext(Connector.Event.Connected(writer::write))

        reader.start()
        writer.start()

        setDisposable(
            Disposable {
                reader.interrupt()
                writer.interrupt()
                socket.closeSafeAsync()
            }
        )
    }
}
