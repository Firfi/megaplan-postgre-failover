package ru.megaplan.db.failover.server.config

import reflect.BeanInfo
import com.thoughtworks.xstream.annotations.XStreamAlias

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.09.12
 * Time: 13:46
 * To change this template use File | Settings | File Templates.
 */
@XStreamAlias("config")
@BeanInfo
class ApplicationConfig(var dbPath: String, var dbAddress: String, var myId: Int, val zooConnectString: String, val restartScript: String) {}
