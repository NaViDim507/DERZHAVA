package com.example.derzhava.data

class EconomyService(
    private val countryDao: CountryDao
) {

    fun stealWorkers40(attackerRuler: String, defenderRuler: String) {
        val att = countryDao.getCountryByRuler(attackerRuler) ?: return
        val def = countryDao.getCountryByRuler(defenderRuler) ?: return

        val stolen = (def.workers * 40) / 100
        if (stolen <= 0) return

        countryDao.insertCountry(def.copy(workers = def.workers - stolen))
        countryDao.insertCountry(att.copy(workers = att.workers + stolen))
    }

    fun stealResources40(attackerRuler: String, defenderRuler: String) {
        val att = countryDao.getCountryByRuler(attackerRuler) ?: return
        val def = countryDao.getCountryByRuler(defenderRuler) ?: return

        val foodStolen = (def.food * 40) / 100
        val woodStolen = (def.wood * 40) / 100
        val mineralStolen = (def.mineral * 40) / 100

        countryDao.insertCountry(
            def.copy(
                food = def.food - foodStolen,
                wood = def.wood - woodStolen,
                mineral = def.mineral - mineralStolen
            )
        )
        countryDao.insertCountry(
            att.copy(
                food = att.food + foodStolen,
                wood = att.wood + woodStolen,
                mineral = att.mineral + mineralStolen
            )
        )
    }

    fun stealMoney40(attackerRuler: String, defenderRuler: String) {
        val att = countryDao.getCountryByRuler(attackerRuler) ?: return
        val def = countryDao.getCountryByRuler(defenderRuler) ?: return

        val stolen = (def.money * 40) / 100
        if (stolen <= 0) return

        countryDao.insertCountry(def.copy(money = def.money - stolen))
        countryDao.insertCountry(att.copy(money = att.money + stolen))
    }
}
