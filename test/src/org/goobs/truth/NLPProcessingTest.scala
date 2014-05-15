package org.goobs.truth

/**
 * A test to make sure we are indexing words in a reasonable way.
 *
 * @author gabor
 */
class NLPProcessingTest extends Test {
  import Utils._

  describe ("An indexer") {
    it ("should index 'at'") {
      val (indexed, _) = index("at")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("at")
    }

    it ("should index 'stock'") {
      val (indexed, _) = index("stock")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("stock")
    }

    it ("should index 'market'") {
      val (indexed, _) = index("market")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("market")
    }

    it ("should index 'stock market' in middle of sentence") {
      val (indexed, _) = index("the stock market crash")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(1)) should be ("stock market")
      wordGloss(indexed(2)) should be ("crash")
    }

    it ("should index 'stock market' at end of sentence") {
      val (indexed, _) = index("the crash of the stock market")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(4)) should be ("stock market")
      indexed.length should be (5)

      val (indexed2, _) = index("the crash of the stock market.")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed2(4)) should be ("stock market")
      indexed.length should be (5)
    }

    it ("should index 'nobel'") {
      val (indexed, _) = index("nobel")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("nobel")
    }

    it ("should index 'prize'") {
      val (indexed, _) = index("prize")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("prize")
    }

    it ("should index 'nobel prize' in middle of sentence") {
      val (indexed, _) = index("the nobel prize was awarded")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(1)) should be ("nobel prize")
      wordGloss(indexed(2)) should be ("be")
    }

    it ("should index 'nobel prize' at end of sentence") {
      val (indexed, _) = index("he won a nobel prize")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(3)) should be ("nobel prize")
    }

    it ("should index proper names") {
      val (indexed, _) = index("John saw Mary")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("John")
      wordGloss(indexed(2)) should be ("mary")
    }

    it ("should handle overlapping indices") {
      val (indexed, _) = index("I like a lot of things")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("I")
      wordGloss(indexed(1)) should be ("like")
      wordGloss(indexed(2)) should be ("a lot of")
      wordGloss(indexed(3)) should be ("thing")
      indexed.length should be (4)

    }
  }

  describe ("A word sense assignment") {
    it ("should mark no sense for 'Bill'") {
      val fact = NatLog.annotate("Bill met Mary yesterday")
      fact.head.getWord(0).getSense should be (0)
      fact.head.getWord(2).getSense should be (0)
    }

    it ("should mark no sense for 'at' / 'the' / etc.") {
      val fact = NatLog.annotate("John met Mary and Jane at the store")
      fact.head.getWord(3).getSense should be (0)
      fact.head.getWord(5).getSense should be (0)
      fact.head.getWord(6).getSense should be (0)
    }

    it ("should tag 'leading' as an adjective") {
      val fact = NatLog.annotate("Both commissioners used to be leading businessmen.")
      fact.head.getWordCount should be (6)
      fact.head.getWord(4).getPos should be ("j")
    }
  }

  describe ("The fact simplifier") {
    it ("should work correctly on 'all dogs go to heaven'") {
      Utils.simplifyQuery("all dogs go to heaven", (x:String) => NatLog.annotate(x).head) should be (Some("all dog go heaven"))
    }
    it ("should work on a selection of actual facts") {
      Utils.simplifyQuery("Pun originally hails from Bima", (x:String) => NatLog.annotate(x).head) should be (Some("pun hail bima"))
      Utils.simplifyQuery("the speaker put the proposed review", (x:String) => NatLog.annotate(x).head) should be (Some("the speaker put the review"))
      Utils.simplifyQuery("further documentaries be also produced on topics", (x:String) => NatLog.annotate(x).head) should be (Some("documentary produce topic"))
      Utils.simplifyQuery("Management positions are held in today", (x:String) => NatLog.annotate(x).head) should be (Some("position hold today"))
      Utils.simplifyQuery("The race was run over a distance of miles", (x:String) => NatLog.annotate(x).head) should be (Some("the race run a distance"))
      Utils.simplifyQuery("stories being split up over several weeks", (x:String) => NatLog.annotate(x).head) should be (Some("story split several week"))
      Utils.simplifyQuery("Two teams participated in the league", (x:String) => NatLog.annotate(x).head) should be (Some("team participate the league"))
      Utils.simplifyQuery("hazards does not manage in accordance", (x:String) => NatLog.annotate(x).head) should be (Some("hazard manage accordance"))
      Utils.simplifyQuery("Scholarships to educate local leaders", (x:String) => NatLog.annotate(x).head) should be (Some("scholarship educate leader"))
      Utils.simplifyQuery("Two teams participated in the league", (x:String) => NatLog.annotate(x).head) should be (Some("team participate the league"))
      Utils.simplifyQuery("Rotten Tomatoes reported 96 % of critics", (x:String) => NatLog.annotate(x).head) should be (Some("tomato report %"))
      Utils.simplifyQuery("prosecutors investigated allegations", (x:String) => NatLog.annotate(x).head) should be (Some("prosecutor investigate allegation"))
      Utils.simplifyQuery("the Rams be outgained by 511 yards", (x:String) => NatLog.annotate(x).head) should be (Some("the rams outgain yard"))
      Utils.simplifyQuery("Blomkamp met with major studios", (x:String) => NatLog.annotate(x).head) should be (Some("blomkamp meet studio"))
      Utils.simplifyQuery("Both roads followed the line of the railway", (x:String) => NatLog.annotate(x).head) should be (Some("both road follow the line"))
      Utils.simplifyQuery("brackets being added to the eaves and overhangs", (x:String) => NatLog.annotate(x).head) should be (Some("bracket add the overhang"))
      Utils.simplifyQuery("contestants nominated other contestants", (x:String) => NatLog.annotate(x).head) should be (Some("contestant nominate contestant"))
      Utils.simplifyQuery("points be followed by goal difference", (x:String) => NatLog.annotate(x).head) should be (Some("point follow difference"))
      Utils.simplifyQuery("Drakshasava found mention", (x:String) => NatLog.annotate(x).head) should be (Some("drakshasava find mention"))
      Utils.simplifyQuery("the pipe organ façade were added to the sanctuary", (x:String) => NatLog.annotate(x).head) should be (Some("the façade add the sanctuary"))
      Utils.simplifyQuery("Li Gang rejected these demands", (x:String) => NatLog.annotate(x).head) should be (Some("gang reject demand"))
      Utils.simplifyQuery("Strong School was moved a list of characters", (x:String) => NatLog.annotate(x).head) should be (Some("school move a list"))
      Utils.simplifyQuery("The school moved to a new two-story house", (x:String) => NatLog.annotate(x).head) should be (Some("the school move a house"))
      Utils.simplifyQuery("Context is provided by the portion", (x:String) => NatLog.annotate(x).head) should be (Some("context provide the portion"))
      Utils.simplifyQuery("ACC POY honors be earned on the day", (x:String) => NatLog.annotate(x).head) should be (Some("honor earn the day"))
      Utils.simplifyQuery("yards be allowed per game", (x:String) => NatLog.annotate(x).head) should be (Some("yard allow game"))
      Utils.simplifyQuery("the Hokies would repeat the performance", (x:String) => NatLog.annotate(x).head) should be (Some("the hokies repeat the performance"))
      Utils.simplifyQuery("the pass is sent in an address", (x:String) => NatLog.annotate(x).head) should be (Some("the pass send address"))
      Utils.simplifyQuery("motor racing could be go as a driver", (x:String) => NatLog.annotate(x).head) should be (Some("racing go a driver"))
      Utils.simplifyQuery("recent analysis suggests for example", (x:String) => NatLog.annotate(x).head) should be (Some("analysis suggest example"))
      Utils.simplifyQuery("shares rose in value", (x:String) => NatLog.annotate(x).head) should be (Some("share rise value"))
      Utils.simplifyQuery("The spiral tube slide be used in the scene", (x:String) => NatLog.annotate(x).head) should be (Some("the slide use the scene"))
      Utils.simplifyQuery("not every discipline was held in every level", (x:String) => NatLog.annotate(x).head) should be (Some("not discipline hold every level"))
      Utils.simplifyQuery("the team discovered evidence of fort buildings", (x:String) => NatLog.annotate(x).head) should be (Some("the team discover evidence"))
      Utils.simplifyQuery("The station continued the country western music", (x:String) => NatLog.annotate(x).head) should be (Some("the station continue the music"))
      Utils.simplifyQuery("elections However was suspended from the party", (x:String) => NatLog.annotate(x).head) should be (Some("election suspend the party"))
      Utils.simplifyQuery("case be heard in court", (x:String) => NatLog.annotate(x).head) should be (Some("case hear court"))
      Utils.simplifyQuery("two seasons be spent at Feethams", (x:String) => NatLog.annotate(x).head) should be (Some("season spend feetham"))
      Utils.simplifyQuery("The virtual currency is called gPotatoes", (x:String) => NatLog.annotate(x).head) should be (Some("the currency call gpotato"))
      Utils.simplifyQuery("βTrCP mediates the degradation of EMI1", (x:String) => NatLog.annotate(x).head) should be (Some("βtrcp mediate the degradation"))
      Utils.simplifyQuery("the award be also won at the same awards", (x:String) => NatLog.annotate(x).head) should be (Some("the award win the award"))
      Utils.simplifyQuery("whose interests staged with a campy anglophilia", (x:String) => NatLog.annotate(x).head) should be (Some("interest stage anglophilia"))
      Utils.simplifyQuery("only 3 minutes be left in the game", (x:String) => NatLog.annotate(x).head) should be (Some("minute leave the game"))
      Utils.simplifyQuery("the kick rolled into the end zone", (x:String) => NatLog.annotate(x).head) should be (Some("the kick roll the zone"))
      Utils.simplifyQuery("the song be based on a competition", (x:String) => NatLog.annotate(x).head) should be (Some("the song base a competition"))
      Utils.simplifyQuery("payments being made to \"bolers", (x:String) => NatLog.annotate(x).head) should be (Some("payment make boler"))
      Utils.simplifyQuery("The film was listed at number 5", (x:String) => NatLog.annotate(x).head) should be (Some("the film list number"))
      Utils.simplifyQuery("20 appearances be made in the 2009–2010 season", (x:String) => NatLog.annotate(x).head) should be (Some("appearance make the season"))
      Utils.simplifyQuery("The results were published in the year", (x:String) => NatLog.annotate(x).head) should be (Some("the result publish the year"))
      Utils.simplifyQuery("The opera opens with a chorus", (x:String) => NatLog.annotate(x).head) should be (Some("the opera open a chorus"))
      Utils.simplifyQuery("the song be put on the charts", (x:String) => NatLog.annotate(x).head) should be (Some("the song put the chart"))
      Utils.simplifyQuery("the case to report to be police", (x:String) => NatLog.annotate(x).head) should be (Some("the case report police"))
      Utils.simplifyQuery("11 locomotives are no longer used in The class", (x:String) => NatLog.annotate(x).head) should be (Some("locomotive use the class"))
      Utils.simplifyQuery("a diploma be received from the AA", (x:String) => NatLog.annotate(x).head) should be (Some("a diploma receive the aa"))
      Utils.simplifyQuery("The mail coach service began operations", (x:String) => NatLog.annotate(x).head) should be (Some("the service begin operation"))
      Utils.simplifyQuery("The group no longer exists in today", (x:String) => NatLog.annotate(x).head) should be (Some("the group exist today"))
      Utils.simplifyQuery("A Wisconsin sports writer touted the signing", (x:String) => NatLog.annotate(x).head) should be (Some("a writer tout the signing"))
      Utils.simplifyQuery("the class was divided into groups", (x:String) => NatLog.annotate(x).head) should be (Some("the class divide group"))
      Utils.simplifyQuery("a link faced opposition", (x:String) => NatLog.annotate(x).head) should be (Some("a link face opposition"))
      Utils.simplifyQuery("a stranglehold be took on the season championship", (x:String) => NatLog.annotate(x).head) should be (Some("a stranglehold take the championship"))
      Utils.simplifyQuery("four period winners played for promotion", (x:String) => NatLog.annotate(x).head) should be (Some("winner play promotion"))
      Utils.simplifyQuery("GVAV and Alkmaar '54 won the championship", (x:String) => NatLog.annotate(x).head) should be (Some("alkmaar win the championship"))
      Utils.simplifyQuery("Stennes mildly protested to Röhm", (x:String) => NatLog.annotate(x).head) should be (Some("stenne protest röhm"))
      Utils.simplifyQuery("eight entrants entered two groups", (x:String) => NatLog.annotate(x).head) should be (Some("entrant enter group"))
      Utils.simplifyQuery("the holy Allucius be written in one document", (x:String) => NatLog.annotate(x).head) should be (Some("the allucius write document"))
      Utils.simplifyQuery("an army weapon be used on wheeled carriage", (x:String) => NatLog.annotate(x).head) should be (Some("weapon use carriage"))
      Utils.simplifyQuery("The estates were classified into two major groups", (x:String) => NatLog.annotate(x).head) should be (Some("the estate classify group"))
      Utils.simplifyQuery("whose life is ruined in an innocent housekeeper", (x:String) => NatLog.annotate(x).head) should be (Some("life ruin housekeeper"))
      Utils.simplifyQuery("Students may seek admission", (x:String) => NatLog.annotate(x).head) should be (Some("student seek admission"))
      Utils.simplifyQuery("Reserves reported a balance of $ 122,189", (x:String) => NatLog.annotate(x).head) should be (Some("reserve report a balance"))
      Utils.simplifyQuery("those views would be considered could contrary", (x:String) => NatLog.annotate(x).head) should be (Some("view consider contrary"))
      Utils.simplifyQuery("Foals toured the UK for an album preview", (x:String) => NatLog.annotate(x).head) should be (Some("foal tour the uk"))
      Utils.simplifyQuery("strong waves destroyed over 100 boats", (x:String) => NatLog.annotate(x).head) should be (Some("wave destroy boat"))
      Utils.simplifyQuery("an increase be traded in exchange", (x:String) => NatLog.annotate(x).head) should be (Some("increase trade exchange"))
      Utils.simplifyQuery("These regiments were considered eligible", (x:String) => NatLog.annotate(x).head) should be (Some("regiment consider eligible"))
      Utils.simplifyQuery("Tai peoples are called 're Dai people", (x:String) => NatLog.annotate(x).head) should be (Some("people be people"))
      Utils.simplifyQuery("a deal be signed at the beginning of May 2009", (x:String) => NatLog.annotate(x).head) should be (Some("a deal sign the beginning"))
      Utils.simplifyQuery("the bill be reintroduced with 27 cosponsors", (x:String) => NatLog.annotate(x).head) should be (Some("the bill reintroduce cosponsor"))
      Utils.simplifyQuery("Purchases have now manages of wetland", (x:String) => NatLog.annotate(x).head) should be (Some("purchase manage wetland"))
      Utils.simplifyQuery("many articles also appeared in books", (x:String) => NatLog.annotate(x).head) should be (Some("many article appear book"))
      Utils.simplifyQuery("the company recorded in the patent office", (x:String) => NatLog.annotate(x).head) should be (Some("the company record the office"))
      Utils.simplifyQuery("This view involves the belief", (x:String) => NatLog.annotate(x).head) should be (Some("view involve the belief"))
      Utils.simplifyQuery("The boys buy some flowers", (x:String) => NatLog.annotate(x).head) should be (Some("the boy buy some flower"))
      Utils.simplifyQuery("a peak of two be reached on the Pop 100 chart", (x:String) => NatLog.annotate(x).head) should be (Some("a peak reach the chart"))
      Utils.simplifyQuery("The law provided for relief", (x:String) => NatLog.annotate(x).head) should be (Some("the law provide relief"))
      Utils.simplifyQuery("the 1917 team finished with a 1–6–2 record", (x:String) => NatLog.annotate(x).head) should be (Some("the team finish a record"))
      Utils.simplifyQuery("the relationship seems in practice", (x:String) => NatLog.annotate(x).head) should be (Some("the relationship seem practice"))
      Utils.simplifyQuery("the fire to set the grass on fire", (x:String) => NatLog.annotate(x).head) should be (Some("the fire set the grass"))
      Utils.simplifyQuery("a positive review be based on 187 reviews", (x:String) => NatLog.annotate(x).head) should be (Some("a review base review"))
      Utils.simplifyQuery("iconic analysis derives from single image", (x:String) => NatLog.annotate(x).head) should be (Some("analysis derive image"))
      Utils.simplifyQuery("The wall climbed from the town", (x:String) => NatLog.annotate(x).head) should be (Some("the wall climb the town"))
      Utils.simplifyQuery("the second floor serves as the IT classrooms", (x:String) => NatLog.annotate(x).head) should be (Some("the floor serve the classroom"))
      Utils.simplifyQuery("the seat be lost at the 1917 election", (x:String) => NatLog.annotate(x).head) should be (Some("the seat lose the election"))
    }

    it ("should generally return 3 word phrases") {
      Utils.simplifyQuery("Atlantis be returns to earth", (x:String) => NatLog.annotate(x).head) should be (Some("atlantis be earth"))
      Utils.simplifyQuery("Australia be World Heritage site", (x:String) => NatLog.annotate(x).head) should be (Some("australia be site"))

    }
  }

}
