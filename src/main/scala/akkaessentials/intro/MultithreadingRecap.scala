package akkaessentials.intro

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MultithreadingRecap extends App {

  // creating thread
   val aThread = new Thread(() => println("I'm a thread"))
  aThread.start()
  aThread.join()

  /*
  val threadHello = new Thread(() => (1 to 1000).foreach(_ => println("hello")))
  val threadBye = new Thread(() => (1 to 1000).foreach(_ => println("bye")))
  threadHello.start()
  threadBye.start() */
  // different runs will have different results

  class BankAccount(private var amount: Int) { // or add @volatile, but only works for primitives

    def withdrdraw(money: Int) = this.amount -= money // not thread safe

    def safeWithdraw(money: Int) = this.synchronized(
      this.amount -= money
    )
  }

  // inter=thread communication
  // wait = notify mechanism

  // Scala Futures

  import scala.concurrent.ExecutionContext.Implicits.global

  val future = Future {
    // on different thread
    42
  }

  future.onComplete {
    case Success(value) => println(f"found meaning of life $value")
    case Failure(_) => println("wrong")
  }

  val aProcessedFuture = future.map(_ + 1)
  val aFlatFuture = future.flatMap(v => Future( v + 2))
  val filterd = future.filter(_ % 2 == 0) // fails with NoSuchElementException

  // for comprehensions

  val aNonsenseFuture = for {
    m <- future
    f <- filterd
  } yield m + f

  // what thread model doesn't address

  // OOP encapsulation only valid in single threaded model

  class BankAccount2(private var amount: Int) {

    def withdrdraw(money: Int) = this.amount -= money // not thread safe
    def deposit(money: Int) = this.amount += money

    def safeWithdraw(money: Int) = this.synchronized(
      this.amount -= money
    )

    def getAmount = amount
  }

  /*
  val account = new BankAccount2(2000)
   for(_ <- 1 to 1000) {
    new Thread(() => account.withdrdraw(1)).start()
  }
  for(_ <- 1 to 1000) {
    new Thread(() => account.deposit(1)).start()

  }
    println(account.getAmount) // OOP is broken if not synchronized

  */

  // but locking introduces deadlocks and livelocks

  // delegating something to a running thread is a pain

  var task: Runnable = null

  val runningThread: Thread = new Thread(() =>
    while (true) {
      while (task == null) {
        runningThread.synchronized({
          println("waiting")
          runningThread.wait()
        })
      }
      task.synchronized{
        println("I have a task")
        task.run()
        task = null
      }
    }
  )

  def delegateTask(r: Runnable) = {
    if (task == null) {
      task = r
    }
    runningThread.synchronized(runningThread.notify())
  }

  runningThread.start()
  Thread.sleep(500)
  delegateTask(() => println(42))
  Thread.sleep(1000)
  delegateTask(() => println(33))

  // tracing and dealing with errors in a multithreaded environment

  // 1M nums between 10 threads

  val futures = (0 to 9).map(i => 100000 * i until 100000  * (i + 1)).map(range => Future{
    if (range.contains(25553)) throw new RuntimeException("Weird number")
    range.sum
})

  val sumFuture = Future.reduceLeft(futures)(_ + _)
  sumFuture.onComplete(println) // hard to find where the failure happened
}
