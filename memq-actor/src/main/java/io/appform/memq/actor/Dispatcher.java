package io.appform.memq.actor;

import com.google.common.collect.Sets;
import lombok.val;

import java.util.Set;
import java.util.stream.Collectors;

interface Dispatcher<M extends Message> extends AutoCloseable {



    void register(Mailbox<M> inMailbox);   //Always executed inside mailbox lock
    void deRegister(Mailbox<M> inMailbox); //Always executed inside mailbox lock
    void triggerDispatch(Mailbox<M> inMailbox); //Always executed inside mailbox lock
    boolean isRunning();
    void close();

    //Always executed inside mailbox lock
    default void dispatch(final Mailbox<M> mailbox) {
        //Find new messages
        val newInOrderedMessages = mailbox.messages.keySet()
                .stream()
                .limit(mailbox.maxConcurrency)
                .collect(Collectors.toSet());
        val newMessageIds = Set.copyOf(Sets.difference(newInOrderedMessages, mailbox.inFlight));
        if (newMessageIds.isEmpty()) {
            if(mailbox.inFlight.size() == mailbox.maxConcurrency) {
                log.warn("Reached max concurrency:{}. Ignoring consumption till inflight messages are consumed",
                        mailbox.maxConcurrency);
            }
            else {
                log.debug("No new messages. Neither is actor stopped. Ignoring spurious dispatch.");
            }
            return;
        }
        mailbox.inFlight.addAll(newMessageIds);
        val messagesToBeDelivered = newMessageIds.stream()
                .map(mailbox.messages::get)
                .toList();
        messagesToBeDelivered.forEach(internalMessage -> mailbox.getActor().getExecutorService().submit(() -> {
            val id = internalMessage.getId();
            try {
                mailbox.getActor().processWithObserver(internalMessage);
            }
            catch (Throwable throwable) {
                log.error("Error processing internalMessage", throwable);
            }
            finally {
                mailbox.releaseMessage(id);
            }
        }));
    }

    default void logError(String message, Throwable error) {
        // Default implementation for error logging
        System.err.println("Dispatcher Error: " + message);
        if (error != null) {
            error.printStackTrace();
        }
    }
}
